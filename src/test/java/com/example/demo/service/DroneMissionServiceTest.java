package com.example.demo.service;

import com.example.demo.entity.Alarm;
import com.example.demo.entity.Device;
import com.example.demo.entity.DroneMission;
import com.example.demo.mapper.AlarmMapper;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.DroneMissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 无人机复核任务状态机测试。
 *
 * 覆盖：
 * - 派发创建任务并进入 DISPATCHED，记录 DRONE_DISPATCHED；
 * - advance 沿状态机推进（EN_ROUTE→ON_SITE→RETURNED→COMPLETED）；
 * - 确认火点 → 事件 drone_confirmed=1、优先级 ≥95，
 *   记录 DRONE_FIRE_CONFIRMED；
 * - 未发现火点 → 记录 DRONE_NO_FIRE_FOUND；
 * - 未到达现场不能确认火点。
 */
public class DroneMissionServiceTest {

    private AlarmMapper alarmMapper;
    private DeviceMapper deviceMapper;
    private DroneMissionMapper missionMapper;
    private AlarmEventLogService eventLogService;
    private DroneMissionService service;

    @BeforeEach
    void setUp() {
        alarmMapper = mock(AlarmMapper.class);
        deviceMapper = mock(DeviceMapper.class);
        missionMapper = mock(DroneMissionMapper.class);
        eventLogService = mock(AlarmEventLogService.class);
        service = new DroneMissionService(
                missionMapper,
                alarmMapper,
                deviceMapper,
                eventLogService,
                new ForestEventPriorityService(
                        mock(com.example.demo.mapper.AncientTreeMapper.class),
                        mock(com.example.demo.mapper.WildlifeHabitatMapper.class)),
                new ForestFireRiskService(),
                new ForestZoneService(
                        mock(com.example.demo.mapper.ForestZoneMapper.class)));
    }

    private Alarm forestAlarm() {
        Alarm alarm = new Alarm();
        alarm.setId(7L);
        alarm.setDeviceId(1001L);
        alarm.setSceneType(AlarmService.SCENE_FOREST);
        alarm.setZoneId(1L);
        alarm.setFireConfidenceScore(70);
        alarm.setFireWeatherScore(50);
        alarm.setAncientTreeImpactScore(10);
        alarm.setWildlifeImpactScore(10);
        alarm.setPriorityScore(0);
        alarm.setDroneConfirmed(0);
        return alarm;
    }

    private Device nodeDevice() {
        Device device = new Device();
        device.setDeviceId(1001L);
        device.setZoneId(1L);
        device.setNodeCode("FS-N-001");
        device.setNodeName("北部核心保护区·FS-N-001");
        device.setLatitude(new BigDecimal("28.1800000"));
        device.setLongitude(new BigDecimal("113.1000000"));
        device.setMapX(300.0);
        device.setMapY(180.0);
        return device;
    }

    private DroneMission onSiteMission() {
        DroneMission mission = new DroneMission();
        mission.setId(99L);
        mission.setAlarmId(7L);
        mission.setState(DroneMissionService.STATE_ON_SITE);
        mission.setDroneId("DRONE-01");
        return mission;
    }

    @Test
    void dispatchCreatesMissionInDispatchedState() {

        when(alarmMapper.selectById(7L)).thenReturn(forestAlarm());
        when(deviceMapper.selectById(1001L)).thenReturn(nodeDevice());

        DroneMission mission = service.dispatch(7L, "DRONE-01");

        assertNotNull(mission);
        assertEquals(DroneMissionService.STATE_DISPATCHED, mission.getState());
        assertEquals("FS-N-001", mission.getTargetNodeCode());
        assertEquals(7L, mission.getAlarmId());

        verify(missionMapper).insert(mission);
        verify(eventLogService).record(
                eq(7L), eq(1001L),
                eq(DroneMissionService.EV_DRONE_DISPATCHED),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void advanceWalksStateMachine() {

        DroneMission mission = onSiteMission();
        mission.setState(DroneMissionService.STATE_DISPATCHED);
        when(missionMapper.selectById(99L)).thenReturn(mission);
        when(alarmMapper.selectById(7L)).thenReturn(forestAlarm());

        service.advance(99L);
        assertEquals(DroneMissionService.STATE_EN_ROUTE, mission.getState());

        service.advance(99L);
        assertEquals(DroneMissionService.STATE_ON_SITE, mission.getState());
        assertNotNull(mission.getArriveTime());

        service.advance(99L);
        assertEquals(DroneMissionService.STATE_RETURNED, mission.getState());

        service.advance(99L);
        assertEquals(DroneMissionService.STATE_COMPLETED, mission.getState());
        assertNotNull(mission.getCompleteTime());
    }

    @Test
    void advanceIsIdempotentWhenCompleted() {

        DroneMission mission = onSiteMission();
        mission.setState(DroneMissionService.STATE_COMPLETED);
        when(missionMapper.selectById(99L)).thenReturn(mission);

        DroneMission result = service.advance(99L);

        assertEquals(DroneMissionService.STATE_COMPLETED, result.getState());
    }

    @Test
    void confirmFireRequiresOnSite() {

        DroneMission mission = onSiteMission();
        mission.setState(DroneMissionService.STATE_EN_ROUTE);
        when(missionMapper.selectById(99L)).thenReturn(mission);

        assertThrows(ResponseStatusException.class,
                () -> service.confirmFire(99L, true, "到达后再确认"));
    }

    @Test
    void confirmFireConfirmedFlagsAlarmAndForcesHighPriority() {

        Alarm alarm = forestAlarm();
        when(missionMapper.selectById(99L)).thenReturn(onSiteMission());
        when(alarmMapper.selectById(7L)).thenReturn(alarm);

        service.confirmFire(99L, true, "航拍确认明火");

        assertEquals(1, alarm.getDroneConfirmed(), "无人机确认后事件必须标记 drone_confirmed=1");
        assertTrue(alarm.getPriorityScore() >= 95,
                "无人机确认火点后优先级强制 ≥95，实际=" + alarm.getPriorityScore());
        assertEquals("RED", alarm.getPriorityLevel());

        ArgumentCaptor<Alarm> captor = ArgumentCaptor.forClass(Alarm.class);
        verify(alarmMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getDroneConfirmed());

        verify(eventLogService).record(
                eq(7L), eq(1001L),
                eq(DroneMissionService.EV_DRONE_FIRE_CONFIRMED),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void confirmFireNoFireKeepsEvidenceAndLogsNoFire() {

        Alarm alarm = forestAlarm();
        when(missionMapper.selectById(99L)).thenReturn(onSiteMission());
        when(alarmMapper.selectById(7L)).thenReturn(alarm);

        service.confirmFire(99L, false, "未发现明火");

        assertEquals(0, alarm.getDroneConfirmed(), "未发现火点不得标记确认");

        ArgumentCaptor<DroneMission> captor =
                ArgumentCaptor.forClass(DroneMission.class);
        verify(missionMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getConfirmedFire().intValue(),
                "任务 confirmedFire 必须为 0");
        assertEquals("未发现明火", captor.getValue().getResultNote());

        verify(eventLogService).record(
                eq(7L), eq(1001L),
                eq(DroneMissionService.EV_DRONE_NO_FIRE_FOUND),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull());
    }
}
