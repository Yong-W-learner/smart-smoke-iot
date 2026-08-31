package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Alarm;
import com.example.demo.entity.AlarmEventLog;
import com.example.demo.entity.Device;
import com.example.demo.entity.SmokeRecord;
import com.example.demo.entity.User;
import com.example.demo.mapper.AlarmEventLogMapper;
import com.example.demo.mapper.AlarmMapper;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.SmokeRecordMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.EventPriorityService;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 高校宿舍管理员工作台。
 *
 * 宿管第一眼要知道：
 * 哪里出问题、谁最紧急、先处理谁。
 *
 * 提供：
 * - /events    紧急事件队列（按 priorityScore 降序）
 * - /matrix    楼栋→楼层→寝室 风险矩阵
 * - /rooms/{id} 单间寝室详情
 *
 * 事件包含两个相互独立的维度：
 * - activeAlarm   环境维度：recover_time IS NULL，烟雾环境尚未恢复；
 * - pendingEvent  处置维度：disposal_state != CLOSED，人工处置尚未关闭。
 * 环境已恢复但处置未关闭的事件，以 DISPOSAL_PENDING 保留在队列与详情中。
 *
 * 所有接口受 /api/admin/** 的 ADMIN 权限保护。
 */
@RestController
@RequestMapping("/api/admin/dormitory")
public class AdminDormitoryController {

    private final DeviceMapper deviceMapper;
    private final AlarmMapper alarmMapper;
    private final SmokeRecordMapper smokeRecordMapper;
    private final AlarmEventLogMapper alarmEventLogMapper;
    private final EventPriorityService priorityService;
    private final UserMapper userMapper;

    public AdminDormitoryController(DeviceMapper deviceMapper,
                                    AlarmMapper alarmMapper,
                                    SmokeRecordMapper smokeRecordMapper,
                                    AlarmEventLogMapper alarmEventLogMapper,
                                    EventPriorityService priorityService,
                                    UserMapper userMapper) {
        this.deviceMapper = deviceMapper;
        this.alarmMapper = alarmMapper;
        this.smokeRecordMapper = smokeRecordMapper;
        this.alarmEventLogMapper = alarmEventLogMapper;
        this.priorityService = priorityService;
        this.userMapper = userMapper;
    }


    /**
     * 紧急事件队列。
     *
     * GET /api/admin/dormitory/events?limit=30
     *
     * 只返回真正需要处理的寝室：
     * ALARM / WARNING / SENSOR_FAULT / OFFLINE / STALE /
     * DISPOSAL_PENDING（待完成处置），NORMAL 一律不进入队列。
     *
     * 两个独立维度：
     * - 环境维度 activeAlarm（recover_time IS NULL）恒为 ALARM，
     *   即使随后 OFFLINE/STALE 也保持最高优先级；
     * - 处置维度 pendingEvent（disposal_state != CLOSED）在环境恢复后
     *   仍以 DISPOSAL_PENDING 保留在队列，避免事件从视野消失，
     *   优先级低于正在告警/预警的事件、高于 NORMAL。
     *
     * 按 priorityScore 降序，让管理员一眼知道先处理谁。
     */
    @GetMapping("/events")
    public List<Map<String, Object>> events(
            @RequestParam(defaultValue = "30") Integer limit) {

        List<Device> devices =
                deviceMapper.selectList(
                        new LambdaQueryWrapper<Device>()
                );

        Map<Long, SmokeRecord> latestMap = latestByDevice();
        Map<Long, Alarm> activeAlarmMap = activeAlarmByDevice();
        Map<Long, Alarm> pendingMap = openDisposalByDevice();
        Map<Long, Integer> recent24h = recentAnomalyByDevice(1);
        Map<Long, Integer> recent7d = recentAnomalyByDevice(7);

        List<Map<String, Object>> result =
                new ArrayList<>();

        for (Device device : devices) {

            Long deviceId = device.getDeviceId();
            SmokeRecord latest = latestMap.get(deviceId);

            /*
             * 两个独立维度：
             * - activeAlarm：环境尚未恢复（recover_time IS NULL）的烟雾告警；
             * - pendingEvent：人工处置尚未关闭（disposal_state != CLOSED）的安全事件。
             * 两者可能是同一条，也可能不同。
             */
            Alarm activeAlarm = activeAlarmMap.get(deviceId);
            Alarm pendingEvent = pendingMap.get(deviceId);

            String riskLevel =
                    priorityService.riskLevel(
                            device,
                            latest,
                            activeAlarm != null,
                            pendingEvent != null
                    );

            if (!priorityService.needsAttention(riskLevel)) {
                continue;
            }

            result.add(
                    buildEvent(
                            device,
                            latest,
                            activeAlarm,
                            pendingEvent,
                            riskLevel,
                            recent24h.getOrDefault(deviceId, 0),
                            recent7d.getOrDefault(deviceId, 0)
                    )
            );
        }

        result.sort(
                (a, b) -> Double.compare(
                        ((Number) b.get("priorityScore")).doubleValue(),
                        ((Number) a.get("priorityScore")).doubleValue()
                )
        );

        int safeLimit =
                limit == null
                        ? 30
                        : Math.max(1, Math.min(limit, 200));

        return result.subList(
                0,
                Math.min(safeLimit, result.size())
        );
    }


    /**
     * 楼栋→楼层→寝室 风险矩阵。
     *
     * GET /api/admin/dormitory/matrix
     *     ?building=1&floor=1&status=ALARM&keyword=101
     */
    @GetMapping("/matrix")
    public Map<String, Object> matrix(
            @RequestParam(required = false) Integer building,
            @RequestParam(required = false) Integer floor,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {

        List<Device> devices =
                deviceMapper.selectList(
                        new LambdaQueryWrapper<Device>()
                );

        Map<Long, SmokeRecord> latestMap = latestByDevice();
        Map<Long, Alarm> activeAlarmMap = activeAlarmByDevice();
        Map<Long, Alarm> pendingMap = openDisposalByDevice();

        /*
         * 矩阵搜索扩展到学生用户名：
         * device.user_id -> sys_user.username，供 keyword 匹配。
         */
        Map<Long, String> userIdToName =
                new HashMap<>();

        for (User user
                : userMapper.selectList(
                        new LambdaQueryWrapper<User>())) {

            userIdToName.put(user.getId(), user.getUsername());
        }

        Map<Integer, Map<Integer, List<Map<String, Object>>>> byBuilding =
                new TreeMap<>();

        for (Device device : devices) {

            if (building != null
                    && !Integer.valueOf(building).equals(device.getBuilding())) {
                continue;
            }

            if (floor != null
                    && !Integer.valueOf(floor).equals(device.getFloor())) {
                continue;
            }

            SmokeRecord latest =
                    latestMap.get(device.getDeviceId());

            Alarm activeAlarm =
                    activeAlarmMap.get(device.getDeviceId());

            Alarm pendingEvent =
                    pendingMap.get(device.getDeviceId());

            String riskLevel =
                    priorityService.riskLevel(
                            device,
                            latest,
                            activeAlarm != null,
                            pendingEvent != null
                    );

            if (StringUtils.hasText(status)
                    && !riskLevel.equalsIgnoreCase(status.trim())) {
                continue;
            }

            String location = buildLocation(device);

            if (StringUtils.hasText(keyword)) {
                String k = keyword.trim().toLowerCase();
                String boundUserName =
                        device.getUserId() == null
                                ? null
                                : userIdToName.get(device.getUserId());
                boolean hit =
                        String.valueOf(device.getDeviceId()).contains(k)
                                || location.toLowerCase().contains(k)
                                || (boundUserName != null
                                && boundUserName.toLowerCase().contains(k));
                if (!hit) {
                    continue;
                }
            }

            Map<String, Object> room = new LinkedHashMap<>();
            room.put("deviceId", device.getDeviceId());
            room.put("room", device.getRoom());
            room.put("sourceType", device.getSourceType());
            room.put("riskLevel", riskLevel);
            room.put("cloudState",
                    latest == null ? null : latest.getCloudState());
            room.put("healthStatus", device.getHealthStatus());
            room.put("riskScore",
                    latest == null ? null : latest.getRiskScore());
            room.put("lastReportTime", device.getLastReportTime());
            room.put("hasActiveAlarm", activeAlarm != null);
            room.put("hasOpenDisposal", pendingEvent != null);
            room.put("environmentState",
                    activeAlarm == null ? "RECOVERED" : "ALARMING");
            room.put("disposalState",
                    pendingEvent == null ? null : normalizeState(pendingEvent));

            int b = device.getBuilding() == null
                    ? 0 : device.getBuilding();
            int f = device.getFloor() == null
                    ? 0 : device.getFloor();

            byBuilding
                    .computeIfAbsent(b, k -> new TreeMap<>())
                    .computeIfAbsent(f, k -> new ArrayList<>())
                    .add(room);
        }

        // 每个楼栋内按房间号升序
        byBuilding.values()
                .forEach(floors -> floors.values()
                        .forEach(rooms -> rooms.sort(
                                (x, y) -> Integer.compare(
                                        numberValue(x.get("room")),
                                        numberValue(y.get("room"))
                                )
                        )));

        List<Map<String, Object>> buildings =
                new ArrayList<>();

        byBuilding.forEach((b, floors) -> {

            Map<String, Object> buildingEntry =
                    new LinkedHashMap<>();
            buildingEntry.put("building", b);

            List<Map<String, Object>> floorList =
                    new ArrayList<>();

            floors.forEach((f, rooms) -> {

                Map<String, Object> floorEntry =
                        new LinkedHashMap<>();
                floorEntry.put("floor", f);
                floorEntry.put("rooms", rooms);
                floorList.add(floorEntry);
            });

            buildingEntry.put("floors", floorList);
            buildings.add(buildingEntry);
        });

        Map<String, Object> result =
                new LinkedHashMap<>();
        result.put("buildings", buildings);
        return result;
    }


    /**
     * 单间寝室详情。
     *
     * GET /api/admin/dormitory/rooms/{deviceId}
     */
    @GetMapping("/rooms/{deviceId}")
    public Map<String, Object> roomDetail(
            @PathVariable Long deviceId) {

        Device device =
                deviceMapper.selectById(deviceId);

        if (device == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "寝室设备不存在"
            );
        }

        Map<Long, SmokeRecord> latestMap = latestByDevice();
        SmokeRecord latest =
                latestMap.get(deviceId);

        List<SmokeRecord> recent =
                smokeRecordMapper.selectList(
                        new LambdaQueryWrapper<SmokeRecord>()
                                .eq(SmokeRecord::getDeviceId, deviceId)
                                .orderByDesc(SmokeRecord::getCollectTime)
                                .last("LIMIT 20")
                );

        Collections.reverse(recent); // 时间升序，便于前端画趋势

        /*
         * 两个独立维度：
         * - activeAlarm：环境尚未恢复（recover_time IS NULL）的烟雾告警；
         * - pendingEvent：人工处置尚未关闭（disposal_state != CLOSED）的安全事件。
         * 即使 recoverTime 已有值，只要 disposalState != CLOSED，
         * 寝室详情仍必须返回该事件的处置字段，允许继续填写结果并关闭。
         */
        Map<Long, Alarm> activeAlarmMap = activeAlarmByDevice();
        Map<Long, Alarm> pendingMap = openDisposalByDevice();

        Alarm activeAlarm = activeAlarmMap.get(deviceId);
        Alarm pendingEvent = pendingMap.get(deviceId);

        Map<Long, Integer> recent24h = recentAnomalyByDevice(1);
        Map<Long, Integer> recent7d = recentAnomalyByDevice(7);

        /*
         * 处置时间线：该设备全部告警事件，按时间升序。
         */
        List<AlarmEventLog> eventLog =
                alarmEventLogMapper.selectList(
                        new LambdaQueryWrapper<AlarmEventLog>()
                                .eq(AlarmEventLog::getDeviceId, deviceId)
                                .orderByAsc(AlarmEventLog::getEventTime)
                                .last("LIMIT 50")
                );

        String riskLevel =
                priorityService.riskLevel(
                        device,
                        latest,
                        activeAlarm != null,
                        pendingEvent != null
                );

        /*
         * 持续时间 / 确认状态：
         * 优先取环境未恢复的告警，其次取待完成处置事件。
         */
        Alarm durationAlarm =
                activeAlarm != null
                        ? activeAlarm
                        : pendingEvent;

        long durationMinutes = 0;
        Integer acknowledged = null;

        if (durationAlarm != null
                && durationAlarm.getAlarmTime() != null) {

            durationMinutes =
                    Duration.between(
                            durationAlarm.getAlarmTime(),
                            LocalDateTime.now()
                    ).toMinutes();

            if (durationMinutes < 0) {
                durationMinutes = 0;
            }

            acknowledged =
                    durationAlarm.getAcknowledged();
        }

        int unacknowledged =
                acknowledged != null
                        && acknowledged == 0
                        ? 1 : 0;

        double score =
                priorityService.priorityScore(
                        riskLevel,
                        latest == null
                                ? null : latest.getRiskScore(),
                        durationMinutes,
                        unacknowledged,
                        recent24h.getOrDefault(deviceId, 0)
                );

        Map<String, Object> result =
                new LinkedHashMap<>();
        result.put("deviceId", deviceId);
        result.put("location", buildLocation(device));
        result.put("sourceType", device.getSourceType());
        result.put("building", device.getBuilding());
        result.put("floor", device.getFloor());
        result.put("room", device.getRoom());
        result.put("status", device.getStatus());
        result.put("healthStatus", device.getHealthStatus());
        result.put("lastReportTime", device.getLastReportTime());
        result.put("riskLevel", riskLevel);
        result.put("priorityScore", round(score));
        result.put("latest", latest);
        result.put("recentRecords", recent);

        /*
         * 环境维度
         */
        result.put("activeAlarm", activeAlarm);
        result.put("activeAlarmId",
                activeAlarm == null ? null : activeAlarm.getId());
        result.put("environmentState",
                activeAlarm == null ? "RECOVERED" : "ALARMING");

        /*
         * 处置维度（人工未关闭的事件）
         */
        result.put("pendingEvent", pendingEvent);
        result.put("pendingAlarmId",
                pendingEvent == null ? null : pendingEvent.getId());

        result.put("acknowledged", acknowledged);
        result.put("unacknowledged", unacknowledged);
        result.put("activeDurationMinutes", durationMinutes);
        result.put("recentAnomaly24h", recent24h.getOrDefault(deviceId, 0));
        result.put("recentAnomaly7d", recent7d.getOrDefault(deviceId, 0));
        result.put("eventLog", eventLog);

        /*
         * 处置状态（人工处置进度，与 recoverTime 相互独立）。
         * 一律取自待完成处置事件 pendingEvent，
         * 而非环境告警 activeAlarm。
         */
        result.put("disposalState",
                pendingEvent == null ? null : normalizeState(pendingEvent));
        result.put("handledBy",
                pendingEvent == null ? null : pendingEvent.getHandledBy());
        result.put("confirmedAt",
                pendingEvent == null ? null : pendingEvent.getConfirmedAt());
        result.put("onSiteAt",
                pendingEvent == null ? null : pendingEvent.getOnSiteAt());
        result.put("closeAt",
                pendingEvent == null ? null : pendingEvent.getCloseAt());
        result.put("disposalRemark",
                pendingEvent == null ? null : pendingEvent.getDisposalRemark());
        return result;
    }


    /* ==================================================
       私有工具
       ================================================== */

    private Map<Long, SmokeRecord> latestByDevice() {

        Map<Long, SmokeRecord> map =
                new HashMap<>();

        for (SmokeRecord record
                : smokeRecordMapper.selectLatestPerDevice()) {

            map.put(record.getDeviceId(), record);
        }

        return map;
    }


    private Map<Long, Alarm> activeAlarmByDevice() {

        List<Alarm> alarms =
                alarmMapper.selectList(
                        new LambdaQueryWrapper<Alarm>()
                                .eq(Alarm::getAlarmType, "SMOKE")
                                .isNull(Alarm::getRecoverTime)
                );

        Map<Long, Alarm> map =
                new HashMap<>();

        for (Alarm alarm : alarms) {
            map.put(alarm.getDeviceId(), alarm);
        }

        return map;
    }


    /**
     * 每台设备最近一条"人工尚未关闭"的烟雾告警
     * （disposal_state IS NULL OR disposal_state != CLOSED）。
     *
     * 与 recoverTime 相互独立：
     * 即使环境已恢复，只要人工处置未关闭，事件仍在其中。
     */
    private Map<Long, Alarm> openDisposalByDevice() {

        List<Alarm> alarms =
                alarmMapper.selectList(
                        new LambdaQueryWrapper<Alarm>()
                                .eq(Alarm::getAlarmType, "SMOKE")
                                .and(w -> w
                                        .isNull(Alarm::getDisposalState)
                                        .or()
                                        .ne(Alarm::getDisposalState, "CLOSED"))
                                .orderByDesc(Alarm::getAlarmTime)
                );

        Map<Long, Alarm> map =
                new HashMap<>();

        /*
         * 按时间倒序遍历，putIfAbsent 保留每台设备最近的一条。
         */
        for (Alarm alarm : alarms) {
            map.putIfAbsent(alarm.getDeviceId(), alarm);
        }

        return map;
    }


    private Map<Long, Integer> recentAnomalyByDevice(int days) {

        Map<Long, Integer> map =
                new HashMap<>();

        for (Map<String, Object> row
                : alarmMapper.countRecentAnomaliesPerDeviceDays(days)) {

            Object id = row.get("deviceId");
            Object cnt = row.get("cnt");

            if (id != null && cnt != null) {
                map.put(
                        ((Number) id).longValue(),
                        ((Number) cnt).intValue()
                );
            }
        }

        return map;
    }


    private Map<String, Object> buildEvent(
            Device device,
            SmokeRecord latest,
            Alarm activeAlarm,
            Alarm pendingEvent,
            String riskLevel,
            int recentAnomalyCount24h,
            int recentAnomalyCount7d) {

        /*
         * 持续时间 / 确认状态：
         * 优先取环境未恢复的告警，其次取待完成处置事件。
         */
        Alarm durationAlarm =
                activeAlarm != null
                        ? activeAlarm
                        : pendingEvent;

        long durationMinutes = 0;
        Integer acknowledged = null;

        if (durationAlarm != null
                && durationAlarm.getAlarmTime() != null) {

            durationMinutes =
                    Duration.between(
                            durationAlarm.getAlarmTime(),
                            LocalDateTime.now()
                    ).toMinutes();

            if (durationMinutes < 0) {
                durationMinutes = 0;
            }

            acknowledged =
                    durationAlarm.getAcknowledged();
        }

        int unacknowledged =
                acknowledged != null
                        && acknowledged == 0
                        ? 1 : 0;

        double score =
                priorityService.priorityScore(
                        riskLevel,
                        latest == null
                                ? null : latest.getRiskScore(),
                        durationMinutes,
                        unacknowledged,
                        recentAnomalyCount24h
                );

        String reason =
                reasonByRiskLevel(
                        riskLevel,
                        activeAlarm,
                        pendingEvent,
                        latest
                );

        Map<String, Object> m =
                new LinkedHashMap<>();
        m.put("deviceId", device.getDeviceId());
        m.put("location", buildLocation(device));
        m.put("sourceType", device.getSourceType());
        m.put("riskLevel", riskLevel);
        m.put("cloudState",
                latest == null ? null : latest.getCloudState());
        m.put("healthStatus", device.getHealthStatus());
        m.put("riskScore",
                latest == null ? null : latest.getRiskScore());
        m.put("lastReportTime", device.getLastReportTime());

        /*
         * 环境维度（recover_time IS NULL）
         */
        m.put("activeAlarmId",
                activeAlarm == null ? null : activeAlarm.getId());
        m.put("activeAlarmTime",
                activeAlarm == null ? null : activeAlarm.getAlarmTime());
        m.put("environmentState",
                activeAlarm == null ? "RECOVERED" : "ALARMING");

        /*
         * 处置维度（disposal_state != CLOSED）
         */
        m.put("pendingAlarmId",
                pendingEvent == null ? null : pendingEvent.getId());
        m.put("pendingAlarmTime",
                pendingEvent == null ? null : pendingEvent.getAlarmTime());

        m.put("durationMinutes", durationMinutes);
        m.put("acknowledged", acknowledged);
        m.put("unacknowledged", unacknowledged);
        m.put("recentAnomalyCount", recentAnomalyCount24h);
        m.put("recentAnomaly24h", recentAnomalyCount24h);
        m.put("recentAnomaly7d", recentAnomalyCount7d);
        m.put("priorityScore", round(score));
        m.put("reason", reason);

        /*
         * 人工处置进度（取自待完成处置事件，与 recoverTime 独立）
         */
        m.put("disposalState",
                pendingEvent == null ? null : normalizeState(pendingEvent));
        m.put("handledBy",
                pendingEvent == null ? null : pendingEvent.getHandledBy());
        m.put("confirmedAt",
                pendingEvent == null ? null : pendingEvent.getConfirmedAt());
        m.put("onSiteAt",
                pendingEvent == null ? null : pendingEvent.getOnSiteAt());
        m.put("closeAt",
                pendingEvent == null ? null : pendingEvent.getCloseAt());
        m.put("disposalRemark",
                pendingEvent == null ? null : pendingEvent.getDisposalRemark());
        return m;
    }


    /**
     * 按当前 riskLevel 选择事件原因来源，禁止张冠李戴：
     *
     * - ALARM            使用环境告警原因（activeAlarm），
     *                    无则回落最新判定原因；
     * - WARNING          只使用最新判定原因（decisionReason），
     *                    不取历史告警 / 待处置原因；
     * - DISPOSAL_PENDING 使用待处置事件原因（pendingEvent），
     *                    无则返回 null，不回落到旧烟雾原因；
     * - OFFLINE / STALE / SENSOR_FAULT 等设备故障类
     *                    一律返回 null，不展示旧烟雾原因
     *                    （前端按健康状态展示"设备离线 / 数据过期"等文案）。
     */
    private String reasonByRiskLevel(
            String riskLevel,
            Alarm activeAlarm,
            Alarm pendingEvent,
            SmokeRecord latest) {

        switch (riskLevel == null ? "NORMAL" : riskLevel) {

            case "ALARM":
                if (activeAlarm != null
                        && StringUtils.hasText(activeAlarm.getReason())) {

                    return activeAlarm.getReason();
                }

                return latest == null
                        ? null : latest.getDecisionReason();

            case "WARNING":
                return latest == null
                        ? null : latest.getDecisionReason();

            case "DISPOSAL_PENDING":
                return pendingEvent != null
                        && StringUtils.hasText(pendingEvent.getReason())
                        ? pendingEvent.getReason()
                        : null;

            default:
                return null;
        }
    }


    /**
     * 处置状态归一化：历史数据（null）按 NEW 处理。
     */
    private String normalizeState(Alarm alarm) {

        String state = alarm.getDisposalState();

        return state == null || state.trim().isEmpty()
                ? "NEW"
                : state.trim().toUpperCase();
    }


    private String buildLocation(Device device) {

        Integer building = device.getBuilding();
        Integer floor = device.getFloor();
        Integer room = device.getRoom();

        if (building != null
                && floor != null
                && room != null) {

            return building + "栋" + floor + "层" + room + "室";
        }

        return "设备" + device.getDeviceId();
    }


    private int numberValue(Object value) {

        return value instanceof Number
                ? ((Number) value).intValue()
                : 0;
    }


    private double round(double value) {

        return Math.round(value * 100.0) / 100.0;
    }
}
