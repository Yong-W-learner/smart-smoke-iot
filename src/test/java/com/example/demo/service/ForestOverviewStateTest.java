package com.example.demo.service;

import com.example.demo.entity.Alarm;
import com.example.demo.entity.Device;
import com.example.demo.entity.SmokeRecord;
import com.example.demo.mapper.AlarmMapper;
import com.example.demo.mapper.AncientTreeMapper;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.DroneMissionMapper;
import com.example.demo.mapper.EcologicalFollowupMapper;
import com.example.demo.mapper.EnvironmentRecordMapper;
import com.example.demo.mapper.ForestZoneMapper;
import com.example.demo.mapper.SmokeRecordMapper;
import com.example.demo.mapper.WildlifeHabitatMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 产品语义收口：节点状态口径 + 统一待办任务队列。
 *
 * 覆盖组合状态：
 * - NORMAL+NORMAL / WARNING+NORMAL → 按云端判定；
 * - NORMAL+OFFLINE / NORMAL+SENSOR_FAULT / NORMAL+STALE → 设备级异常
 *   优先，最后一次 NORMAL 判定不得覆盖成"正常"；
 * - ALARM+OFFLINE → 设备级异常优先计入队列，但最后一次报警判定保留，
 *   活动 ALARM 不被 OFFLINE 覆盖丢失。
 */
public class ForestOverviewStateTest {

    private Device device(Long id, String code, int status,
                          String health, Long zoneId) {
        Device d = new Device();
        d.setDeviceId(id);
        d.setNodeCode(code);
        d.setStatus(status);
        d.setHealthStatus(health);
        d.setZoneId(zoneId);
        return d;
    }

    private SmokeRecord smoke(Long deviceId, String cloudState) {
        SmokeRecord r = new SmokeRecord();
        r.setDeviceId(deviceId);
        r.setCloudState(cloudState);
        return r;
    }

    /* ==================== effectiveNodeState 组合口径 ==================== */

    @Test
    void normalOnlyWhenOnlineHealthyAndCloudNormal() {
        Device d = device(1L, "FS-N-001", 1, "NORMAL", 1L);
        assertEquals("NORMAL",
                ForestOverviewService.effectiveNodeState(d, "NORMAL"));
    }

    @Test
    void warningPreservedWhenDeviceHealthy() {
        Device d = device(2L, "FS-N-002", 1, "NORMAL", 1L);
        assertEquals("WARNING",
                ForestOverviewService.effectiveNodeState(d, "WARNING"));
    }

    @Test
    void lastNormalCloudDoesNotCoverDeviceOffline() {
        Device offline = device(3L, "FS-N-003", 0, "OFFLINE", 1L);
        assertEquals("OFFLINE",
                ForestOverviewService.effectiveNodeState(offline, "NORMAL"));

        Device healthOffline = device(4L, "FS-N-004", 1, "OFFLINE", 1L);
        assertEquals("OFFLINE",
                ForestOverviewService.effectiveNodeState(healthOffline, "NORMAL"));
    }

    @Test
    void lastNormalCloudDoesNotCoverSensorFault() {
        Device fault = device(5L, "FS-N-005", 1, "SENSOR_FAULT", 1L);
        assertEquals("SENSOR_FAULT",
                ForestOverviewService.effectiveNodeState(fault, "NORMAL"));
    }

    @Test
    void lastNormalCloudDoesNotCoverStale() {
        Device stale = device(6L, "FS-N-006", 1, "STALE", 1L);
        assertEquals("STALE",
                ForestOverviewService.effectiveNodeState(stale, "NORMAL"));
    }

    @Test
    void alarmOnOfflineDeviceIsNotCoveredByOffline() {
        // 设备级异常优先用于统计，但最后一次 ALARM 判定不会被丢失。
        Device offlineAlarm = device(7L, "FS-N-007", 0, "OFFLINE", 1L);
        assertEquals("OFFLINE",
                ForestOverviewService.effectiveNodeState(offlineAlarm, "ALARM"));
    }

    @Test
    void healthyNodeWithoutCloudRecordIsUnknownNotNormal() {
        Device d = device(8L, "FS-N-008", 1, "NORMAL", 1L);
        assertEquals("UNKNOWN",
                ForestOverviewService.effectiveNodeState(d, null));
    }


    /* ==================== 总览统计口径（正常/异常/队列） ==================== */

    /**
     * 90 节点场景抽象：1 正常 + 1 离线 + 1 传感器故障 + 1 离线但最后判定为 ALARM。
     * 期望：正常 1、异常 3、预警 0；统一队列含 SENSOR_FAULT 与 OFFLINE，
     * 离线节点的最后 ALARM 判定保留在任务项中。
     */
    @Test
    void overviewCountsNormalOnlyOnIntersection() {
        Device devNormal = device(1001L, "FS-N-001", 1, "NORMAL", 1L);
        Device devOffline = device(1002L, "FS-N-002", 0, "OFFLINE", 1L);
        Device devFault = device(1003L, "FS-N-003", 1, "SENSOR_FAULT", 1L);
        Device devAlarmOffline = device(1004L, "FS-N-004", 0, "OFFLINE", 1L);
        List<Device> devices = List.of(
                devNormal, devOffline, devFault, devAlarmOffline);

        ForestOverviewService service = buildService(
                devices,
                List.of(
                        smoke(1001L, "NORMAL"),
                        smoke(1002L, "NORMAL"),
                        smoke(1003L, "NORMAL"),
                        smoke(1004L, "ALARM")),
                List.of(),   // 活动火险
                List.of());  // 待完成处置

        Map<String, Object> result = service.overview();

        assertEquals(1L, result.get("normalNodeCount"));
        assertEquals(3L, result.get("abnormalNodeCount"));
        assertEquals(0L, result.get("warningNodeCount"));
        assertEquals(0L, result.get("activeAlarmCount"));
        assertEquals(3L, result.get("pendingTaskCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queue =
                (List<Map<String, Object>>) result.get("taskQueue");

        assertEquals("SENSOR_FAULT", queue.get(0).get("taskType"));
        assertEquals("OFFLINE", queue.get(1).get("taskType"));
        assertEquals("OFFLINE", queue.get(2).get("taskType"));

        Map<String, Object> alarmOfflineTask = queue.get(2);
        assertEquals("FS-N-004", alarmOfflineTask.get("nodeCode"));
        assertEquals("ALARM", alarmOfflineTask.get("cloudState"),
                "离线节点的最后 ALARM 判定必须保留，不被 OFFLINE 覆盖丢失");
    }

    /**
     * 统一待办队列固定排序：
     * 活动火险（RED > ORANGE > YELLOW）> 待完成处置 > SENSOR_FAULT
     * > OFFLINE > STALE > 生态回访。
     */
    @Test
    void taskQueueOrdersFireLevelsBeforeDeviceAnomalies() {
        Device devFault = device(1003L, "FS-N-003", 1, "SENSOR_FAULT", 1L);
        Device devOffline = device(1002L, "FS-N-002", 0, "OFFLINE", 1L);
        List<Device> devices = List.of(devFault, devOffline);

        Alarm red = new Alarm();
        red.setId(10L);
        red.setSceneType(AlarmService.SCENE_FOREST);
        red.setPriorityLevel(ForestEventPriorityService.LEVEL_RED);
        red.setPriorityScore(95);
        red.setZoneId(1L);

        Alarm yellow = new Alarm();
        yellow.setId(11L);
        yellow.setSceneType(AlarmService.SCENE_FOREST);
        yellow.setPriorityLevel(ForestEventPriorityService.LEVEL_YELLOW);
        yellow.setPriorityScore(60);
        yellow.setZoneId(1L);

        ForestOverviewService service = buildService(
                devices,
                List.of(smoke(1002L, "NORMAL"), smoke(1003L, "NORMAL")),
                List.of(red, yellow),
                List.of());

        Map<String, Object> result = service.overview();

        assertEquals(2L, result.get("activeAlarmCount"));
        assertEquals(1L, result.get("redEventCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queue =
                (List<Map<String, Object>>) result.get("taskQueue");

        assertEquals("FIRE_ACTIVE", queue.get(0).get("taskType"));
        assertEquals(10L, queue.get(0).get("id"));
        assertEquals("FIRE_ACTIVE", queue.get(1).get("taskType"));
        assertEquals(11L, queue.get(1).get("id"));
        assertEquals("SENSOR_FAULT", queue.get(2).get("taskType"));
        assertEquals("OFFLINE", queue.get(3).get("taskType"));
    }


    /* ==================== 组装 ==================== */

    private ForestOverviewService buildService(
            List<Device> devices,
            List<SmokeRecord> latestSmoke,
            List<Alarm> activeEvents,
            List<Alarm> disposalEvents) {

        DeviceMapper deviceMapper = mock(DeviceMapper.class);
        when(deviceMapper.selectList(any())).thenReturn(devices);
        when(deviceMapper.selectById(anyLong())).thenReturn(
                devices.isEmpty() ? null : devices.get(0));

        SmokeRecordMapper smokeRecordMapper = mock(SmokeRecordMapper.class);
        when(smokeRecordMapper.selectLatestPerDevice())
                .thenReturn(latestSmoke);

        AlarmMapper alarmMapper = mock(AlarmMapper.class);
        // overview() 会对活动火险就地排序，必须返回可变列表。
        when(alarmMapper.selectList(any()))
                .thenReturn(new ArrayList<>(activeEvents),
                        new ArrayList<>(disposalEvents));

        EcologicalFollowupMapper followupMapper =
                mock(EcologicalFollowupMapper.class);
        when(followupMapper.selectList(any())).thenReturn(List.of());

        ForestZoneService forestZoneService = mock(ForestZoneService.class);
        when(forestZoneService.listZones()).thenReturn(List.of());
        when(forestZoneService.zoneNameById(anyLong())).thenReturn("Z01");

        ForestOverviewService service = new ForestOverviewService(
                mock(ForestZoneMapper.class),
                deviceMapper,
                alarmMapper,
                mock(AncientTreeMapper.class),
                mock(WildlifeHabitatMapper.class),
                followupMapper,
                mock(DroneMissionMapper.class),
                mock(EnvironmentRecordMapper.class),
                smokeRecordMapper,
                mock(FireWeatherService.class),
                forestZoneService,
                mock(ForestEventPriorityService.class));

        return service;
    }
}
