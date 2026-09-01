package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Alarm;
import com.example.demo.entity.Device;
import com.example.demo.entity.DroneMission;
import com.example.demo.entity.EcologicalFollowup;
import com.example.demo.entity.EnvironmentRecord;
import com.example.demo.entity.ForestZone;
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
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 森林公园生态安全指挥台总览统计。
 *
 * 透明规则，非AI：
 * 基于分区 / 节点 / 火险事件 / 生态资源四类对象做计数与聚合，
 * 不进行任何预测性推断。
 *
 * 顶层统计（7 项核心口径）：
 * - 监测节点：nodeCount（森林监测节点总数）
 * - 正常节点：normalNodeCount（最新云端判定 NORMAL）
 * - 烟雾预警：warningNodeCount（最新云端判定 WARNING）
 * - 活动火情：activeAlarmCount（FOREST 且环境未恢复）
 * - 设备异常：abnormalNodeCount（离线 / 传感器故障等设备级异常）
 * - 受威胁生态资源：threatenedResourceCount（活动事件影响到的
 *   古树 / 栖息地资源，跨事件去重）
 * - 待处置事件：pendingTaskCount（活动事件中尚未完成无人机复核的）
 */
@Service
public class ForestOverviewService {

    private final ForestZoneMapper forestZoneMapper;
    private final DeviceMapper deviceMapper;
    private final AlarmMapper alarmMapper;
    private final AncientTreeMapper ancientTreeMapper;
    private final WildlifeHabitatMapper wildlifeHabitatMapper;
    private final EcologicalFollowupMapper followupMapper;
    private final DroneMissionMapper droneMissionMapper;
    private final EnvironmentRecordMapper environmentRecordMapper;
    private final SmokeRecordMapper smokeRecordMapper;
    private final FireWeatherService fireWeatherService;
    private final ForestZoneService forestZoneService;
    private final ForestEventPriorityService forestEventPriorityService;

    public ForestOverviewService(ForestZoneMapper forestZoneMapper,
                                 DeviceMapper deviceMapper,
                                 AlarmMapper alarmMapper,
                                 AncientTreeMapper ancientTreeMapper,
                                 WildlifeHabitatMapper wildlifeHabitatMapper,
                                 EcologicalFollowupMapper followupMapper,
                                 DroneMissionMapper droneMissionMapper,
                                 EnvironmentRecordMapper environmentRecordMapper,
                                 SmokeRecordMapper smokeRecordMapper,
                                 FireWeatherService fireWeatherService,
                                 ForestZoneService forestZoneService,
                                 ForestEventPriorityService forestEventPriorityService) {
        this.forestZoneMapper = forestZoneMapper;
        this.deviceMapper = deviceMapper;
        this.alarmMapper = alarmMapper;
        this.ancientTreeMapper = ancientTreeMapper;
        this.wildlifeHabitatMapper = wildlifeHabitatMapper;
        this.followupMapper = followupMapper;
        this.droneMissionMapper = droneMissionMapper;
        this.environmentRecordMapper = environmentRecordMapper;
        this.smokeRecordMapper = smokeRecordMapper;
        this.fireWeatherService = fireWeatherService;
        this.forestZoneService = forestZoneService;
        this.forestEventPriorityService = forestEventPriorityService;
    }

    /**
     * 指挥台总览数据。
     */
    public Map<String, Object> overview() {

        long zoneCount = forestZoneMapper.selectCount(null);

        List<Device> allNodes = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .isNotNull(Device::getNodeCode)
        );
        long nodeCount = allNodes.size();

        Map<Long, SmokeRecord> latestSmoke = latestSmokePerDevice();

        long onlineCount = allNodes.stream()
                .filter(d -> Integer.valueOf(1).equals(d.getStatus()))
                .count();
        long abnormalNodeCount = allNodes.stream()
                .filter(this::deviceAbnormal)
                .count();

        /*
         * 正常节点口径（本轮修正）：
         * device.status==1 && healthStatus==NORMAL && 最新 cloudState==NORMAL
         * 三者同时成立才算"运行正常"。
         * 设备级异常（离线 / 数据过期 / 传感器故障）不得被最后一次 NORMAL
         * 烟雾判定覆盖成正常。
         */
        long warningNodeCount = allNodes.stream()
                .filter(d -> "WARNING".equals(
                        effectiveNodeState(d, cloudStateOf(d, latestSmoke))))
                .count();
        long normalNodeCount = allNodes.stream()
                .filter(d -> "NORMAL".equals(
                        effectiveNodeState(d, cloudStateOf(d, latestSmoke))))
                .count();

        long treeCount = ancientTreeMapper.selectCount(null);
        long habitatCount = wildlifeHabitatMapper.selectCount(null);

        long pendingFollowupCount = followupMapper.selectCount(
                new LambdaQueryWrapper<EcologicalFollowup>()
                        .in(EcologicalFollowup::getState,
                                EcologicalFollowupService.STATE_PENDING,
                                EcologicalFollowupService.STATE_IN_PROGRESS)
        );

        long activeDroneCount = droneMissionMapper.selectCount(
                new LambdaQueryWrapper<DroneMission>()
                        .ne(DroneMission::getState,
                                DroneMissionService.STATE_COMPLETED)
        );

        /*
         * 活动火险事件：scene_type=FOREST 且环境尚未恢复。
         * 统一待办队列中，活动火险按等级排序：
         * RED > ORANGE > YELLOW > LOW，同级按优先级总分降序。
         */
        List<Alarm> activeEvents = alarmMapper.selectList(
                new LambdaQueryWrapper<Alarm>()
                        .eq(Alarm::getSceneType, AlarmService.SCENE_FOREST)
                        .isNull(Alarm::getRecoverTime)
        );
        activeEvents.sort((a, b) -> {
            int byLevel = Integer.compare(
                    eventLevelRank(a.getPriorityLevel()),
                    eventLevelRank(b.getPriorityLevel()));
            if (byLevel != 0) {
                return byLevel;
            }
            return Integer.compare(
                    nullSafeInt(b.getPriorityScore()),
                    nullSafeInt(a.getPriorityScore()));
        });

        long activeAlarmCount = activeEvents.size();

        long redEventCount = activeEvents.stream()
                .filter(e -> ForestEventPriorityService.LEVEL_RED
                        .equals(e.getPriorityLevel()))
                .count();

        long orangeEventCount = activeEvents.stream()
                .filter(e -> ForestEventPriorityService.LEVEL_ORANGE
                        .equals(e.getPriorityLevel()))
                .count();

        /*
         * 活动火险任务项（含生态影响摘要与受影响资源），
         * 同时作为 topEvents（地图受威胁资源高亮）与统一待办队列的活动火险段。
         */
        Set<String> threatenedCodes = new LinkedHashSet<>();
        List<Map<String, Object>> fireTasks = new ArrayList<>();

        for (Alarm event : activeEvents) {

            Map<String, Object> view = enrichEvent(event);
            view.put("taskType", TASK_FIRE_ACTIVE);
            view.put("typeLabel", "活动火险");

            for (ForestEventPriorityService.ResourceImpact impact
                    : affectedResources(event)) {
                if (impact.code() != null) {
                    threatenedCodes.add(impact.code());
                }
            }

            fireTasks.add(view);
        }

        long threatenedResourceCount = threatenedCodes.size();

        List<Map<String, Object>> topEvents = fireTasks.size() > 5
                ? new ArrayList<>(fireTasks.subList(0, 5))
                : fireTasks;

        /*
         * 统一运维待办队列：
         * 活动火险(RED>ORANGE>YELLOW) > 待完成火险处置 > SENSOR_FAULT
         * > OFFLINE > STALE > 待完成生态回访。
         */
        List<Map<String, Object>> taskQueue =
                buildTaskQueue(fireTasks, allNodes, latestSmoke);

        long pendingTaskCount = taskQueue.size();

        Map<String, Object> result = new LinkedHashMap<>();

        // 7 项核心统计
        result.put("nodeCount", nodeCount);
        result.put("normalNodeCount", normalNodeCount);
        result.put("warningNodeCount", warningNodeCount);
        result.put("activeAlarmCount", activeAlarmCount);
        result.put("abnormalNodeCount", abnormalNodeCount);
        result.put("threatenedResourceCount", threatenedResourceCount);
        result.put("pendingTaskCount", pendingTaskCount);

        // 补充信息
        result.put("zoneCount", zoneCount);
        result.put("onlineCount", onlineCount);
        result.put("treeCount", treeCount);
        result.put("habitatCount", habitatCount);
        result.put("redEventCount", redEventCount);
        result.put("orangeEventCount", orangeEventCount);
        result.put("pendingFollowupCount", pendingFollowupCount);
        result.put("activeDroneCount", activeDroneCount);
        result.put("topEvents", topEvents);
        result.put("taskQueue", taskQueue);
        result.put("zoneWeather", zoneWeather());

        return result;
    }

    /**
     * 事件视图：补齐分区 / 节点 / 生态影响摘要 / 受影响资源 / 持续时长。
     */
    private Map<String, Object> enrichEvent(Alarm event) {

        Device device = event.getDeviceId() == null
                ? null : deviceMapper.selectById(event.getDeviceId());

        List<ForestEventPriorityService.ResourceImpact> affected =
                affectedResources(event);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", event.getId());
        item.put("deviceId", event.getDeviceId());
        item.put("zoneId", event.getZoneId());
        item.put("zoneName",
                forestZoneService.zoneNameById(event.getZoneId()));
        item.put("nodeCode",
                device == null ? null : device.getNodeCode());
        item.put("nodeName",
                device == null ? null : device.getNodeName());
        item.put("alarmTime", event.getAlarmTime());
        item.put("reason", event.getReason());
        item.put("location", event.getLocation());
        item.put("priorityScore", event.getPriorityScore());
        item.put("priorityLevel", event.getPriorityLevel());
        item.put("fireConfidenceScore", event.getFireConfidenceScore());
        item.put("fireWeatherScore", event.getFireWeatherScore());
        item.put("ancientTreeImpactScore",
                event.getAncientTreeImpactScore());
        item.put("wildlifeImpactScore",
                event.getWildlifeImpactScore());
        item.put("priorityReason", event.getPriorityReason());
        item.put("droneConfirmed", event.getDroneConfirmed());
        item.put("disposalState", event.getDisposalState());
        item.put("acknowledged", event.getAcknowledged());
        item.put("ecoSummary", ecoSummary(affected));
        item.put("affectedResources", affected);
        item.put("durationText", durationText(event.getAlarmTime()));

        return item;
    }

    /**
     * 事件点附近命中分档的生态资源（跨分区真实距离重算，事件位置固定）。
     */
    private List<ForestEventPriorityService.ResourceImpact>
            affectedResources(Alarm event) {

        Device device = event.getDeviceId() == null
                ? null : deviceMapper.selectById(event.getDeviceId());

        if (device == null
                || device.getLatitude() == null
                || device.getLongitude() == null) {
            return List.of();
        }

        return forestEventPriorityService.impactScores(
                event.getZoneId(),
                device.getLatitude(),
                device.getLongitude()
        ).affectedResources();
    }

    /**
     * 生态影响摘要文案，例如：
     * "附近一级古树 AT-001 86m / CORE鸟类繁殖区 142m"。
     */
    private String ecoSummary(
            List<ForestEventPriorityService.ResourceImpact> affected) {

        if (affected == null || affected.isEmpty()) {
            return null;
        }

        String joined = affected.stream()
                .limit(2)
                .map(this::resourceText)
                .collect(Collectors.joining(" / "));

        if (affected.size() > 2) {
            joined += " 等" + affected.size() + "处资源";
        }

        return joined;
    }

    private String resourceText(
            ForestEventPriorityService.ResourceImpact impact) {

        long meters = Math.round(impact.distanceMeters());

        if (ForestEventPriorityService.RESOURCE_TYPE_TREE
                .equals(impact.type())) {
            return "附近" + impact.protectionLevel() + "古树 "
                    + impact.code() + " " + meters + "m";
        }

        return impact.protectionLevel()
                + impact.name() + " " + meters + "m";
    }

    /**
     * 事件持续时长文案：30分钟 / 2小时15分。
     */
    private String durationText(LocalDateTime start) {

        if (start == null) {
            return null;
        }

        long minutes = Math.max(0,
                Duration.between(start, LocalDateTime.now()).toMinutes());

        if (minutes < 60) {
            return minutes + "分钟";
        }

        long hours = minutes / 60;
        long remain = minutes % 60;

        return remain == 0
                ? hours + "小时"
                : hours + "小时" + remain + "分";
    }

    /**
     * 设备级异常：离线（status!=1）或健康状态非 NORMAL（传感器故障等）。
     */
    private boolean deviceAbnormal(Device device) {

        if (!Integer.valueOf(1).equals(device.getStatus())) {
            return true;
        }

        String health = device.getHealthStatus();

        return health != null && !"NORMAL".equalsIgnoreCase(health);
    }

    private String cloudStateOf(Device device,
                                Map<Long, SmokeRecord> latestSmoke) {

        SmokeRecord latest = latestSmoke.get(device.getDeviceId());

        return latest == null ? null : latest.getCloudState();
    }

    private Map<Long, SmokeRecord> latestSmokePerDevice() {

        Map<Long, SmokeRecord> map = new LinkedHashMap<>();

        for (SmokeRecord record : smokeRecordMapper.selectLatestPerDevice()) {
            map.put(record.getDeviceId(), record);
        }

        return map;
    }

    /**
     * 各分区火险气象数据（最新环境记录 + 气象评分）。
     */
    private List<Map<String, Object>> zoneWeather() {

        List<Map<String, Object>> list = new ArrayList<>();

        for (ForestZone zone : forestZoneService.listZones()) {

            int score = fireWeatherService.score(zone.getId());
            EnvironmentRecord latest = latestRecord(zone.getId());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("zoneId", zone.getId());
            item.put("zoneName", zone.getZoneName());
            item.put("fireWeatherScore", score);
            item.put("temperature",
                    latest == null ? null : latest.getTemperature());
            item.put("humidity",
                    latest == null ? null : latest.getHumidity());
            item.put("soilMoisture",
                    latest == null ? null : latest.getSoilMoisture());
            item.put("windSpeed",
                    latest == null ? null : latest.getWindSpeed());
            item.put("rainfallMm",
                    latest == null ? null : latest.getRainfallMm());
            item.put("recordTime",
                    latest == null ? null : latest.getRecordTime());

            list.add(item);
        }

        return list;
    }

    private EnvironmentRecord latestRecord(Long zoneId) {

        return environmentRecordMapper.selectOne(
                new LambdaQueryWrapper<EnvironmentRecord>()
                        .eq(EnvironmentRecord::getZoneId, zoneId)
                        .orderByDesc(EnvironmentRecord::getRecordTime)
                        .last("LIMIT 1")
        );
    }


    /* ==================== 统一运维待办队列 ==================== */

    private static final String TASK_FIRE_ACTIVE = "FIRE_ACTIVE";
    private static final String TASK_FIRE_DISPOSAL = "FIRE_DISPOSAL";
    private static final String TASK_SENSOR_FAULT = "SENSOR_FAULT";
    private static final String TASK_OFFLINE = "OFFLINE";
    private static final String TASK_STALE = "STALE";
    private static final String TASK_FOLLOWUP = "FOLLOWUP";

    /**
     * 事件等级排序：RED > ORANGE > YELLOW > LOW。
     */
    private int eventLevelRank(String level) {

        if (ForestEventPriorityService.LEVEL_RED.equals(level)) {
            return 0;
        }
        if (ForestEventPriorityService.LEVEL_ORANGE.equals(level)) {
            return 1;
        }
        if (ForestEventPriorityService.LEVEL_YELLOW.equals(level)) {
            return 2;
        }
        return 3;
    }

    private int nullSafeInt(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 节点最终有效状态：
     * 设备级异常（离线 / 数据过期 / 传感器故障）优先于最新烟雾判定，
     * 不得被最后一次 NORMAL 烟雾判定覆盖成正常；
     * 设备级正常时才展示最新云端烟雾判定。
     */
    public static String effectiveNodeState(Device device,
                                            String cloudState) {

        if (!Integer.valueOf(1).equals(device.getStatus())) {
            return "OFFLINE";
        }

        String health = device.getHealthStatus();

        if ("OFFLINE".equalsIgnoreCase(health)) {
            return "OFFLINE";
        }
        if ("SENSOR_FAULT".equalsIgnoreCase(health)) {
            return "SENSOR_FAULT";
        }
        if ("STALE".equalsIgnoreCase(health)) {
            return "STALE";
        }

        return cloudState == null ? "UNKNOWN" : cloudState.toUpperCase();
    }

    /**
     * 统一运维待办队列：
     * 活动火险(RED>ORANGE>YELLOW) > 待完成火险处置 > SENSOR_FAULT
     * > OFFLINE > STALE > 待完成生态回访。
     */
    private List<Map<String, Object>> buildTaskQueue(
            List<Map<String, Object>> fireTasks,
            List<Device> allNodes,
            Map<Long, SmokeRecord> latestSmoke) {

        List<Map<String, Object>> queue = new ArrayList<>(fireTasks);

        queue.addAll(disposalTasks());
        queue.addAll(deviceTasks(allNodes, latestSmoke,
                TASK_SENSOR_FAULT, "传感器异常"));
        queue.addAll(deviceTasks(allNodes, latestSmoke,
                TASK_OFFLINE, "设备离线"));
        queue.addAll(deviceTasks(allNodes, latestSmoke,
                TASK_STALE, "数据过期"));
        queue.addAll(followupTasks());

        return queue;
    }

    /**
     * 待完成火险处置：环境已恢复但处置流程未闭环（disposalState != CLOSED）。
     */
    private List<Map<String, Object>> disposalTasks() {

        List<Alarm> recovered = alarmMapper.selectList(
                new LambdaQueryWrapper<Alarm>()
                        .eq(Alarm::getSceneType, AlarmService.SCENE_FOREST)
                        .isNotNull(Alarm::getRecoverTime)
                        .ne(Alarm::getDisposalState,
                                AlarmDisposalService.STATE_CLOSED)
                        .orderByDesc(Alarm::getRecoverTime)
        );

        List<Map<String, Object>> list = new ArrayList<>();

        for (Alarm event : recovered) {
            Map<String, Object> view = enrichEvent(event);
            view.put("taskType", TASK_FIRE_DISPOSAL);
            view.put("typeLabel", "待完成处置");
            list.add(view);
        }

        return list;
    }

    /**
     * 设备级异常任务项（传感器故障 / 离线 / 数据过期）。
     */
    private List<Map<String, Object>> deviceTasks(
            List<Device> allNodes,
            Map<Long, SmokeRecord> latestSmoke,
            String taskType,
            String typeLabel) {

        List<Map<String, Object>> list = new ArrayList<>();

        for (Device device : allNodes) {

            String state = effectiveNodeState(
                    device, cloudStateOf(device, latestSmoke));

            if (!taskType.equals(state)) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskType", taskType);
            item.put("typeLabel", typeLabel);
            item.put("id", device.getDeviceId());
            item.put("deviceId", device.getDeviceId());
            item.put("nodeCode", device.getNodeCode());
            item.put("nodeName", device.getNodeName());
            item.put("zoneId", device.getZoneId());
            item.put("zoneName",
                    forestZoneService.zoneNameById(device.getZoneId()));
            item.put("healthStatus", device.getHealthStatus());
            item.put("lastReportTime", device.getLastReportTime());
            item.put("cloudState",
                    cloudStateOf(device, latestSmoke));

            list.add(item);
        }

        return list;
    }

    /**
     * 待完成生态回访任务。
     */
    private List<Map<String, Object>> followupTasks() {

        List<EcologicalFollowup> followups = followupMapper.selectList(
                new LambdaQueryWrapper<EcologicalFollowup>()
                        .in(EcologicalFollowup::getState,
                                EcologicalFollowupService.STATE_PENDING,
                                EcologicalFollowupService.STATE_IN_PROGRESS)
                        .orderByAsc(EcologicalFollowup::getId)
        );

        List<Map<String, Object>> list = new ArrayList<>();

        for (EcologicalFollowup followup : followups) {

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskType", TASK_FOLLOWUP);
            item.put("typeLabel", "待生态回访");
            item.put("id", followup.getId());
            item.put("alarmId", followup.getAlarmId());
            item.put("assetType", followup.getAssetType());
            item.put("assetCode", followup.getAssetCode());
            item.put("assetName", followup.getAssetName());
            item.put("zoneId", followup.getZoneId());
            item.put("zoneName", followup.getZoneName());
            item.put("state", followup.getState());
            item.put("handler", followup.getHandler());
            item.put("dueTime", followup.getDueTime());

            list.add(item);
        }

        return list;
    }
}
