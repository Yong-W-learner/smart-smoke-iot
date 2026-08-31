package com.example.demo.controller;

import com.example.demo.entity.Alarm;
import com.example.demo.entity.Device;
import com.example.demo.entity.SmokeRecord;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * 管理员总览"楼栋态势"测试。
 *
 * 关键约束：
 * - 楼栋态势必须纳入"未关闭处置事件"：
 *   即使环境已恢复（recoverTime 已有），只要处置未关闭
 *   （disposalState != CLOSED），寝室不得按 NORMAL 统计，
 *   必须计入 pendingDisposalRooms / buildingStats.disposalPending。
 * - 存在 DISPOSAL_PENDING 时，楼栋态势不得显示"全部正常"。
 */
@ExtendWith(MockitoExtension.class)
public class AdminOverviewControllerTest {

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private AlarmMapper alarmMapper;

    @Mock
    private SmokeRecordMapper smokeRecordMapper;

    @Mock
    private EventPriorityService priorityService;

    @InjectMocks
    private AdminOverviewController controller;

    private Device device(Long id, int building, int floor, int room) {
        Device device = new Device();
        device.setDeviceId(id);
        device.setBuilding(building);
        device.setFloor(floor);
        device.setRoom(room);
        device.setHealthStatus("NORMAL");
        device.setSourceType("DEMO");
        return device;
    }

    private SmokeRecord record(Long deviceId) {
        SmokeRecord record = new SmokeRecord();
        record.setDeviceId(deviceId);
        record.setCloudState("NORMAL");
        record.setRiskScore(5.0);
        return record;
    }

    private Alarm recoveredButNotClosed(Long id, Long deviceId) {
        Alarm alarm = new Alarm();
        alarm.setId(id);
        alarm.setDeviceId(deviceId);
        alarm.setAlarmType("SMOKE");
        alarm.setRecoverTime(LocalDateTime.now()); // 环境已恢复
        alarm.setDisposalState("ACKNOWLEDGED");     // 处置未关闭
        return alarm;
    }

    private void stubBaseCounts() {
        when(deviceMapper.selectCount(any())).thenReturn(3L);
        when(userMapper.selectCount(any())).thenReturn(20L);
        when(alarmMapper.selectCount(any())).thenReturn(0L);
        when(smokeRecordMapper.selectOne(any())).thenReturn(record(101L));
    }

    /**
     * 三台设备均处于"环境已恢复但处置未关闭"：
     * 楼栋态势应全部计入待闭环，而非 NORMAL。
     */
    @Test
    void recoveredButNotClosedCountedAsPendingDisposalNotNormal() {

        stubBaseCounts();

        List<Device> devices = List.of(
                device(101L, 1, 1, 101),
                device(102L, 1, 1, 102),
                device(103L, 1, 1, 103)
        );
        when(deviceMapper.selectList(any())).thenReturn(devices);
        when(smokeRecordMapper.selectLatestPerDevice())
                .thenReturn(List.of(
                        record(101L), record(102L), record(103L)
                ));

        /*
         * overview() 内 dormitoryRoomStats 调用 selectList 两次：
         * 第一次（活动告警 recoverTime IS NULL）→ 空；
         * 第二次（未关闭处置 disposalState != CLOSED）→ 三条已恢复但未关闭的告警。
         */
        when(alarmMapper.selectList(any()))
                .thenReturn(
                        Collections.emptyList(),
                        List.of(
                                recoveredButNotClosed(1L, 101L),
                                recoveredButNotClosed(2L, 102L),
                                recoveredButNotClosed(3L, 103L)
                        )
                );

        when(priorityService.riskLevel(
                any(Device.class), any(SmokeRecord.class),
                anyBoolean(), anyBoolean()))
                .thenReturn("DISPOSAL_PENDING");

        Map<String, Object> result = controller.overview();

        assertEquals(3, ((Number) result.get("pendingDisposalRooms")).intValue(),
                "待闭环寝室应全部统计为 DISPOSAL_PENDING");
        assertEquals(0, ((Number) result.get("normalRooms")).intValue(),
                "存在待闭环处置时，不得按 NORMAL 统计");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> buildingStats =
                (List<Map<String, Object>>) result.get("buildingStats");

        assertEquals(1, buildingStats.size());
        assertEquals(3, ((Number) buildingStats.get(0).get("disposalPending")).intValue(),
                "楼栋态势 buildingStats 必须输出 disposalPending");
        assertEquals(0, ((Number) buildingStats.get(0).get("normal")).intValue(),
                "楼栋态势不得把待闭环寝室显示为正常");
    }

    /**
     * 活动告警（环境未恢复）时，即使处置未关闭，
     * 仍按 ALARM 统计（最高优先级），不重复计入待闭环。
     */
    @Test
    void activeAlarmOutranksDisposalPendingInStats() {

        stubBaseCounts();

        List<Device> devices = List.of(device(101L, 1, 1, 101));
        when(deviceMapper.selectList(any())).thenReturn(devices);
        when(smokeRecordMapper.selectLatestPerDevice())
                .thenReturn(List.of(record(101L)));
        when(alarmMapper.selectList(any()))
                .thenReturn(Collections.emptyList());

        when(priorityService.riskLevel(
                any(Device.class), any(SmokeRecord.class),
                anyBoolean(), anyBoolean()))
                .thenReturn("ALARM");

        Map<String, Object> result = controller.overview();

        assertEquals(1, ((Number) result.get("alarmRooms")).intValue());
        assertEquals(0, ((Number) result.get("pendingDisposalRooms")).intValue(),
                "活动告警按 ALARM 统计，不得重复计入待闭环");
    }
}
