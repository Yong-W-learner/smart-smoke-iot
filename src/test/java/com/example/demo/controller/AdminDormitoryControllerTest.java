package com.example.demo.controller;

import com.example.demo.entity.Alarm;
import com.example.demo.entity.Device;
import com.example.demo.entity.SmokeRecord;
import com.example.demo.entity.User;
import com.example.demo.mapper.AlarmEventLogMapper;
import com.example.demo.mapper.AlarmMapper;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.SmokeRecordMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.EventPriorityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 寝室风险矩阵筛选测试。
 *
 * 关键约束：
 * - matrix 必须尊重 building / status 筛选参数；
 * - 自动刷新携带当前筛选条件请求时，后端返回结果不得被无过滤数据覆盖，
 *   即 status 过滤后只返回该状态的寝室、building 过滤后只返回该楼栋。
 */
@ExtendWith(MockitoExtension.class)
public class AdminDormitoryControllerTest {

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private AlarmMapper alarmMapper;

    @Mock
    private SmokeRecordMapper smokeRecordMapper;

    @Mock
    private AlarmEventLogMapper alarmEventLogMapper;

    @Mock
    private EventPriorityService priorityService;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AdminDormitoryController controller;

    private Device device(Long id, int building, int floor, int room) {
        Device device = new Device();
        device.setDeviceId(id);
        device.setBuilding(building);
        device.setFloor(floor);
        device.setRoom(room);
        device.setSourceType("DEMO");
        return device;
    }

    private SmokeRecord record(Long deviceId) {
        SmokeRecord record = new SmokeRecord();
        record.setDeviceId(deviceId);
        record.setCloudState("NORMAL");
        record.setRiskScore(10.0);
        return record;
    }

    private void stubThreeDevicesWithRisk(
            java.util.function.Function<Device, String> risk) {

        List<Device> devices = List.of(
                device(101L, 1, 1, 101),
                device(102L, 2, 1, 201),
                device(103L, 1, 1, 103)
        );

        when(deviceMapper.selectList(any())).thenReturn(devices);
        when(smokeRecordMapper.selectLatestPerDevice())
                .thenReturn(List.of(
                        record(101L), record(102L), record(103L)
                ));
        when(alarmMapper.selectList(any()))
                .thenReturn(Collections.emptyList());
        when(userMapper.selectList(any()))
                .thenReturn(Collections.emptyList());

        when(priorityService.riskLevel(
                any(Device.class), any(SmokeRecord.class),
                anyBoolean(), anyBoolean()))
                .thenAnswer(inv -> risk.apply(inv.getArgument(0)));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> roomsOf(Map<String, Object> matrix) {
        List<Map<String, Object>> buildings =
                (List<Map<String, Object>>) matrix.get("buildings");
        return buildings.stream()
                .flatMap(b -> ((List<Map<String, Object>>) b.get("floors")).stream())
                .flatMap(f -> ((List<Map<String, Object>>) f.get("rooms")).stream())
                .toList();
    }

    @Test
    void matrixWithStatusFilterReturnsOnlyThatStatus() {

        stubThreeDevicesWithRisk(
                dev -> dev.getDeviceId().equals(101L)
                        ? "ALARM" : "NORMAL");

        Map<String, Object> result =
                controller.matrix(null, null, "ALARM", null);

        List<Map<String, Object>> rooms = roomsOf(result);

        assertEquals(1, rooms.size(),
                "status=ALARM 只应返回 ALARM 状态的寝室");
        assertEquals(101L, rooms.get(0).get("deviceId"));
        assertEquals("ALARM", rooms.get(0).get("riskLevel"));
    }

    @Test
    void matrixWithBuildingFilterReturnsOnlyThatBuilding() {

        stubThreeDevicesWithRisk(
                dev -> "NORMAL");

        Map<String, Object> result =
                controller.matrix(1, null, null, null);

        List<Map<String, Object>> rooms = roomsOf(result);

        assertEquals(2, rooms.size(),
                "building=1 只应返回 1 栋的寝室");
        assertTrue(rooms.stream()
                .allMatch(r -> r.get("deviceId") != null
                        && (Long) r.get("deviceId") != 102L));
    }

    @Test
    void matrixWithoutFilterReturnsAllBuildings() {

        stubThreeDevicesWithRisk(
                dev -> "NORMAL");

        Map<String, Object> result =
                controller.matrix(null, null, null, null);

        List<Map<String, Object>> rooms = roomsOf(result);

        assertEquals(3, rooms.size(),
                "无筛选时应返回全部寝室");
    }

    /* ---------- 学生用户名搜索 ---------- */

    private User user(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        return u;
    }

    @Test
    void matrixKeywordMatchesBoundStudentUsername() {

        Device d101 = device(101L, 1, 1, 101);
        d101.setUserId(9L);

        when(deviceMapper.selectList(any())).thenReturn(List.of(d101));
        when(smokeRecordMapper.selectLatestPerDevice())
                .thenReturn(List.of(record(101L)));
        when(alarmMapper.selectList(any()))
                .thenReturn(Collections.emptyList());
        when(userMapper.selectList(any()))
                .thenReturn(List.of(user(9L, "wangxiao")));
        when(priorityService.riskLevel(
                any(Device.class), any(SmokeRecord.class),
                anyBoolean(), anyBoolean()))
                .thenReturn("NORMAL");

        Map<String, Object> result =
                controller.matrix(null, null, null, "wang");

        List<Map<String, Object>> rooms = roomsOf(result);

        assertEquals(1, rooms.size(),
                "按学生用户名搜索应命中绑定该账号的寝室");
        assertEquals(101L, rooms.get(0).get("deviceId"));
    }

    /* ---------- 事件原因按 riskLevel 选择来源 ---------- */

    private Alarm openAlarm(Long id, Long deviceId,
                            String reason, String state) {
        Alarm a = new Alarm();
        a.setId(id);
        a.setDeviceId(deviceId);
        a.setAlarmType("SMOKE");
        a.setReason(reason);
        a.setDisposalState(state);
        return a;
    }

    private void stubSingleNormalDeviceForEvents(SmokeRecord latest,
                                                 List<Alarm> alarms) {
        Device d101 = device(101L, 1, 1, 101);
        d101.setHealthStatus("NORMAL");

        when(deviceMapper.selectList(any())).thenReturn(List.of(d101));
        when(smokeRecordMapper.selectLatestPerDevice())
                .thenReturn(List.of(latest));
        when(alarmMapper.selectList(any())).thenReturn(alarms);
        when(alarmMapper.countRecentAnomaliesPerDeviceDays(anyInt()))
                .thenReturn(Collections.emptyList());
        when(priorityService.needsAttention(anyString()))
                .thenReturn(true);
    }

    @Test
    void warningEventReasonComesFromLatestDecision() {

        SmokeRecord latest = record(101L);
        latest.setCloudState("WARNING");
        latest.setDecisionReason("预警：浓度升高");
        stubSingleNormalDeviceForEvents(
                latest,
                List.of(openAlarm(9L, 101L, "历史报警原因", "ACKNOWLEDGED")));

        when(priorityService.riskLevel(
                any(Device.class), any(SmokeRecord.class),
                anyBoolean(), anyBoolean()))
                .thenReturn("WARNING");
        when(priorityService.priorityScore(
                anyString(), any(), anyLong(), anyInt(), anyInt()))
                .thenReturn(50.0);

        List<Map<String, Object>> events = controller.events(10);

        assertEquals(1, events.size());
        assertEquals("WARNING", events.get(0).get("riskLevel"));
        assertEquals("预警：浓度升高", events.get(0).get("reason"),
                "WARNING 只使用最新判定原因，不得取历史告警/待处置原因");
    }

    @Test
    void disposalPendingEventUsesPendingAlarmReason() {

        SmokeRecord latest = record(101L);
        latest.setCloudState("NORMAL");
        latest.setDecisionReason("旧烟雾判定原因（不应展示）");
        stubSingleNormalDeviceForEvents(
                latest,
                List.of(openAlarm(9L, 101L, "处置：等待现场复核", "ON_SITE")));

        when(priorityService.riskLevel(
                any(Device.class), any(SmokeRecord.class),
                anyBoolean(), anyBoolean()))
                .thenReturn("DISPOSAL_PENDING");
        when(priorityService.priorityScore(
                anyString(), any(), anyLong(), anyInt(), anyInt()))
                .thenReturn(20.0);

        List<Map<String, Object>> events = controller.events(10);

        assertEquals(1, events.size());
        assertEquals("DISPOSAL_PENDING", events.get(0).get("riskLevel"));
        assertEquals("处置：等待现场复核", events.get(0).get("reason"),
                "DISPOSAL_PENDING 使用待处置事件原因，不得回落到旧烟雾原因");
    }
}
