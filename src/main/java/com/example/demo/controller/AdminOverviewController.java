package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Alarm;
import com.example.demo.entity.Device;
import com.example.demo.entity.SmokeRecord;
import com.example.demo.entity.User;
import com.example.demo.mapper.AlarmMapper;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.SmokeRecordMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.EventPriorityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员总览统计。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminOverviewController {

    private final DeviceMapper deviceMapper;
    private final UserMapper userMapper;
    private final AlarmMapper alarmMapper;
    private final SmokeRecordMapper smokeRecordMapper;
    private final EventPriorityService priorityService;

    public AdminOverviewController(DeviceMapper deviceMapper,
                                   UserMapper userMapper,
                                   AlarmMapper alarmMapper,
                                   SmokeRecordMapper smokeRecordMapper,
                                   EventPriorityService priorityService) {
        this.deviceMapper = deviceMapper;
        this.userMapper = userMapper;
        this.alarmMapper = alarmMapper;
        this.smokeRecordMapper = smokeRecordMapper;
        this.priorityService = priorityService;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Long deviceCount = deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>()
        );

        Long onlineCount = deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getStatus, 1)
        );

        Long abnormalDeviceCount = deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>()
                        .ne(Device::getHealthStatus, "NORMAL")
        );

        Long studentCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, "resident")
        );

        Long adminCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, "admin")
        );

        Long activeAlarmCount = alarmMapper.selectCount(
                new LambdaQueryWrapper<Alarm>()
                        .isNull(Alarm::getRecoverTime)
                        .eq(Alarm::getAlarmType, "SMOKE")
        );

        Long unacknowledgedAlarmCount = alarmMapper.selectCount(
                new LambdaQueryWrapper<Alarm>()
                        .isNull(Alarm::getRecoverTime)
                        .eq(Alarm::getAlarmType, "SMOKE")
                        .eq(Alarm::getAcknowledged, 0)
        );

        SmokeRecord latest = smokeRecordMapper.selectOne(
                new LambdaQueryWrapper<SmokeRecord>()
                        .orderByDesc(SmokeRecord::getCollectTime)
                        .last("LIMIT 1")
        );

        /*
         * 宿舍工作台全局统计：
         * 每台设备按 riskLevel 归并；
         * 同时按楼栋维度聚合（楼栋态势）。
         */
        Map<String, Object> dormStats =
                dormitoryRoomStats();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deviceCount", deviceCount);
        result.put("onlineCount", onlineCount);
        result.put("abnormalDeviceCount", abnormalDeviceCount);
        result.put("studentCount", studentCount);
        result.put("adminCount", adminCount);
        result.put("activeAlarmCount", activeAlarmCount);
        result.put("unacknowledgedAlarmCount", unacknowledgedAlarmCount);
        result.put("latestRecord", latest);

        result.put("monitoredRooms", dormStats.get("monitoredRooms"));
        result.put("normalRooms", dormStats.get("normalRooms"));
        result.put("warningRooms", dormStats.get("warningRooms"));
        result.put("alarmRooms", dormStats.get("alarmRooms"));
        result.put("sensorFaultRooms", dormStats.get("sensorFaultRooms"));
        result.put("offlineRooms", dormStats.get("offlineRooms"));
        result.put("staleRooms", dormStats.get("staleRooms"));
        result.put("pendingDisposalRooms", dormStats.get("pendingDisposalRooms"));

        result.put("buildingStats", dormStats.get("buildingStats"));
        return result;
    }


    /**
     * 按 riskLevel 统计寝室数量：
     * 监测寝室 / 正常 / WARNING / ALARM / SENSOR_FAULT / OFFLINE / STALE。
     *
     * 额外按楼栋聚合 buildingStats（楼栋态势），
     * 供管理员右侧面板展示每栋楼的寝室风险分布。
     */
    private Map<String, Object> dormitoryRoomStats() {

        List<Device> devices =
                deviceMapper.selectList(
                        new LambdaQueryWrapper<Device>()
                );

        Map<Long, SmokeRecord> latestMap =
                new HashMap<>();

        for (SmokeRecord record
                : smokeRecordMapper.selectLatestPerDevice()) {

            latestMap.put(record.getDeviceId(), record);
        }

        /*
         * 存在活动告警（recover_time IS NULL）的设备，
         * 无论健康状态如何，一律按 ALARM 统计。
         */
        Map<Long, Boolean> activeAlarmMap =
                new HashMap<>();

        for (Alarm alarm
                : alarmMapper.selectList(
                        new LambdaQueryWrapper<Alarm>()
                                .eq(Alarm::getAlarmType, "SMOKE")
                                .isNull(Alarm::getRecoverTime))) {

            activeAlarmMap.put(alarm.getDeviceId(), true);
        }

        /*
         * 存在"人工尚未关闭"处置事件（disposal_state != CLOSED）的设备，
         * 与 recoverTime 相互独立：
         * 即使环境已恢复，只要人工处置未闭环，仍计入"待闭环"。
         */
        Map<Long, Boolean> pendingDisposalMap =
                new HashMap<>();

        for (Alarm alarm
                : alarmMapper.selectList(
                        new LambdaQueryWrapper<Alarm>()
                                .eq(Alarm::getAlarmType, "SMOKE")
                                .and(w -> w
                                        .isNull(Alarm::getDisposalState)
                                        .or()
                                        .ne(Alarm::getDisposalState, "CLOSED")))) {

            pendingDisposalMap.put(alarm.getDeviceId(), true);
        }

        long normal = 0;
        long warning = 0;
        long alarm = 0;
        long sensorFault = 0;
        long offline = 0;
        long stale = 0;
        long pendingDisposal = 0;

        /*
         * 楼栋维度聚合：building -> 各风险等级寝室数。
         */
        Map<Integer, long[]> buildingAcc =
                new LinkedHashMap<>();

        for (Device device : devices) {

            Long deviceId = device.getDeviceId();

            String riskLevel =
                    priorityService.riskLevel(
                            device,
                            latestMap.get(deviceId),
                            activeAlarmMap.containsKey(deviceId),
                            pendingDisposalMap.containsKey(deviceId)
                    );

            switch (riskLevel) {

                case "ALARM":
                    alarm++;
                    break;

                case "WARNING":
                    warning++;
                    break;

                case "SENSOR_FAULT":
                    sensorFault++;
                    break;

                case "OFFLINE":
                    offline++;
                    break;

                case "STALE":
                    stale++;
                    break;

                case "DISPOSAL_PENDING":
                    pendingDisposal++;
                    break;

                default:
                    normal++;
                    break;
            }

            Integer building = device.getBuilding();

            if (building == null) {
                continue;
            }

            long[] acc =
                    buildingAcc.computeIfAbsent(
                            building,
                            b -> new long[8]
                    );

            /*
             * acc 下标：0=total 1=normal 2=warning
             * 3=alarm 4=sensorFault 5=offline 6=stale
             * 7=pendingDisposal（待闭环）
             */
            acc[0]++;

            switch (riskLevel) {

                case "ALARM":
                    acc[3]++;
                    break;

                case "WARNING":
                    acc[2]++;
                    break;

                case "SENSOR_FAULT":
                    acc[4]++;
                    break;

                case "OFFLINE":
                    acc[5]++;
                    break;

                case "STALE":
                    acc[6]++;
                    break;

                case "DISPOSAL_PENDING":
                    acc[7]++;
                    break;

                default:
                    acc[1]++;
                    break;
            }
        }

        List<Map<String, Object>> buildingStats =
                new ArrayList<>();

        for (Map.Entry<Integer, long[]> entry
                : buildingAcc.entrySet()) {

            long[] acc = entry.getValue();

            Map<String, Object> stat =
                    new LinkedHashMap<>();
            stat.put("building", entry.getKey());
            stat.put("total", acc[0]);
            stat.put("normal", acc[1]);
            stat.put("warning", acc[2]);
            stat.put("alarm", acc[3]);
            stat.put("sensorFault", acc[4]);
            stat.put("offline", acc[5]);
            stat.put("stale", acc[6]);
            stat.put("disposalPending", acc[7]);
            buildingStats.add(stat);
        }

        Map<String, Object> stats =
                new LinkedHashMap<>();
        stats.put("monitoredRooms", (long) devices.size());
        stats.put("normalRooms", normal);
        stats.put("warningRooms", warning);
        stats.put("alarmRooms", alarm);
        stats.put("sensorFaultRooms", sensorFault);
        stats.put("offlineRooms", offline);
        stats.put("staleRooms", stale);
        stats.put("pendingDisposalRooms", pendingDisposal);
        stats.put("buildingStats", buildingStats);
        return stats;
    }
}
