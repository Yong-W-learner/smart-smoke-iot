package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.demo.entity.Alarm;
import com.example.demo.entity.Device;
import com.example.demo.mapper.AlarmMapper;
import com.example.demo.mapper.DeviceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AlarmService 森林火险事件创建路径测试。
 *
 * 覆盖：
 * - 森林节点（zone_id 非空）触发 ALARM → scene_type=FOREST、
 *   完整写入火险可信度 / 气象评分 / 生态影响 / 事件优先级；
 * - 历史宿舍设备（zone_id 为空）→ scene_type=DORM_LEGACY（兼容保留）；
 * - 已有活动告警时不重复创建；
 * - NORMAL 恢复活动告警并记录恢复时间线；
 * - 设备不存在时静默跳过（不抛异常、不建告警）；
 * - 位置文案：森林节点用"分区名 · 节点编号"、宿舍设备用"X栋X层X室"。
 */
@ExtendWith(MockitoExtension.class)
public class AlarmServiceForestTest {

    @Mock
    private AlarmMapper alarmMapper;

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private AlarmEventLogService eventLogService;

    @Mock
    private ForestFireRiskService forestFireRiskService;

    @Mock
    private FireWeatherService fireWeatherService;

    @Mock
    private ForestEventPriorityService forestEventPriorityService;

    @Mock
    private ForestZoneService forestZoneService;

    @InjectMocks
    private AlarmService service;

    private Device forestNode() {
        Device device = new Device();
        device.setDeviceId(1002L);
        device.setZoneId(1L);
        device.setNodeCode("FS-N-002");
        device.setNodeName("北部核心保护区·FS-N-002");
        device.setLatitude(new BigDecimal("28.1800000"));
        device.setLongitude(new BigDecimal("113.1000000"));
        return device;
    }

    private Device legacyDormDevice() {
        Device device = new Device();
        device.setDeviceId(1083L);
        device.setZoneId(null);
        device.setBuilding(3);
        device.setFloor(5);
        device.setRoom(503);
        return device;
    }

    private void stubForestScoring() {
        when(forestFireRiskService.fireConfidenceScore(
                any(), any(), any(), anyBoolean())).thenReturn(90);
        when(fireWeatherService.score(1L)).thenReturn(60);
        when(forestEventPriorityService.impactScores(
                eq(1L), any(), any()))
                .thenReturn(new ForestEventPriorityService.ImpactScores(20, 10));
        when(forestEventPriorityService.priorityScore(
                90, 60, 20, 10, false)).thenReturn(85);
        when(forestEventPriorityService.priorityLevel(85))
                .thenReturn("RED");
        when(forestEventPriorityService.priorityReason(
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn("规则判定：优先级85(RED)；无人机复核未执行");
    }

    @Test
    void forestAlarmGetsForestSceneAndScoring() {

        when(deviceMapper.selectById(1002L)).thenReturn(forestNode());
        when(alarmMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(forestZoneService.zoneNameById(1L))
                .thenReturn("北部核心保护区");
        stubForestScoring();

        service.handleSmokeDecision(
                1002L, "ALARM", "烟雾浓度异常", 85.0, "ALARM");

        ArgumentCaptor<Alarm> captor = ArgumentCaptor.forClass(Alarm.class);
        verify(alarmMapper).insert(captor.capture());

        Alarm alarm = captor.getValue();

        assertEquals(AlarmService.SCENE_FOREST, alarm.getSceneType(),
                "森林节点告警必须标记 scene_type=FOREST");
        assertEquals(1L, alarm.getZoneId());
        assertEquals("北部核心保护区 · FS-N-002", alarm.getLocation(),
                "森林位置文案 = 分区名称 · 节点编号");
        assertEquals(90, alarm.getFireConfidenceScore());
        assertEquals(60, alarm.getFireWeatherScore());
        assertEquals(20, alarm.getAncientTreeImpactScore());
        assertEquals(10, alarm.getWildlifeImpactScore());
        assertEquals(85, alarm.getPriorityScore());
        assertEquals("RED", alarm.getPriorityLevel());
        assertEquals(0, alarm.getDroneConfirmed());
        assertEquals(0, alarm.getAcknowledged());
        assertNull(alarm.getAckTime());
        assertNull(alarm.getRecoverTime());
    }

    @Test
    void legacyDeviceGetsDormLegacyScene() {

        when(deviceMapper.selectById(1083L)).thenReturn(legacyDormDevice());
        when(alarmMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        service.handleSmokeDecision(
                1083L, "ALARM", "烟雾浓度异常", 85.0, "ALARM");

        ArgumentCaptor<Alarm> captor = ArgumentCaptor.forClass(Alarm.class);
        verify(alarmMapper).insert(captor.capture());

        Alarm alarm = captor.getValue();

        assertEquals(AlarmService.SCENE_DORM_LEGACY, alarm.getSceneType(),
                "历史宿舍设备保持 DORM_LEGACY");
        assertNull(alarm.getZoneId());
        assertEquals("3栋5层503室", alarm.getLocation());
        assertNull(alarm.getPriorityScore(),
                "宿舍事件不参与森林火险评分");
    }

    @Test
    void duplicateAlarmNotCreatedWhenActiveExists() {

        Alarm active = new Alarm();
        active.setId(7L);
        active.setRecoverTime(null);

        when(deviceMapper.selectById(1002L)).thenReturn(forestNode());
        when(alarmMapper.selectOne(any(Wrapper.class))).thenReturn(active);

        service.handleSmokeDecision(
                1002L, "ALARM", "烟雾浓度异常", 85.0, "ALARM");

        verify(alarmMapper, never()).insert(any(Alarm.class));
    }

    @Test
    void normalStateRecoversActiveAlarm() {

        Alarm active = new Alarm();
        active.setId(7L);
        active.setDeviceId(1002L);
        active.setRecoverTime(null);

        when(deviceMapper.selectById(1002L)).thenReturn(forestNode());
        when(alarmMapper.selectOne(any(Wrapper.class))).thenReturn(active);

        service.handleSmokeDecision(
                1002L, "NORMAL", "烟雾浓度恢复正常", null, null);

        assertNotNull(active.getRecoverTime(),
                "NORMAL 判定必须填写 recoverTime");
        verify(alarmMapper).updateById(active);
        verify(eventLogService).record(
                eq(7L), eq(1002L),
                eq(AlarmDisposalService.EV_SMOKE_RECOVERED),
                anyString(), anyString(), isNull());
    }

    @Test
    void unknownDeviceIgnoredSilently() {

        when(deviceMapper.selectById(9999L)).thenReturn(null);

        service.handleSmokeDecision(
                9999L, "ALARM", "烟雾浓度异常", 85.0, "ALARM");

        verify(alarmMapper, never()).insert(any(Alarm.class));
        verify(alarmMapper, never()).selectOne(any(Wrapper.class));
    }

    @Test
    void legacyDeviceWithoutRoomUsesDeviceIdFallback() {

        Device device = new Device();
        device.setDeviceId(1090L);
        device.setZoneId(null);

        when(deviceMapper.selectById(1090L)).thenReturn(device);
        when(alarmMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        service.handleSmokeDecision(
                1090L, "ALARM", "烟雾浓度异常", null, null);

        ArgumentCaptor<Alarm> captor = ArgumentCaptor.forClass(Alarm.class);
        verify(alarmMapper).insert(captor.capture());

        assertEquals("设备1090", captor.getValue().getLocation());
    }

    @Test
    void forestNodeWithoutZoneNameUsesNodeCodeFallback() {

        Device device = forestNode();
        when(deviceMapper.selectById(1002L)).thenReturn(device);
        when(alarmMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(forestZoneService.zoneNameById(1L)).thenReturn("");
        stubForestScoring();

        service.handleSmokeDecision(
                1002L, "ALARM", "烟雾浓度异常", 85.0, "ALARM");

        ArgumentCaptor<Alarm> captor = ArgumentCaptor.forClass(Alarm.class);
        verify(alarmMapper).insert(captor.capture());

        assertEquals("森林监测节点FS-N-002",
                captor.getValue().getLocation());
    }
}
