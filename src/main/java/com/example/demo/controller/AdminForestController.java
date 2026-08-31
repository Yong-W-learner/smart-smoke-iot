package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Alarm;
import com.example.demo.entity.AlarmEventLog;
import com.example.demo.entity.AncientTree;
import com.example.demo.entity.AncientTreeInspection;
import com.example.demo.entity.Device;
import com.example.demo.entity.DroneMission;
import com.example.demo.entity.EcologicalFollowup;
import com.example.demo.entity.EnvironmentRecord;
import com.example.demo.entity.ForestZone;
import com.example.demo.entity.WildlifeHabitat;
import com.example.demo.mapper.AlarmEventLogMapper;
import com.example.demo.mapper.AlarmMapper;
import com.example.demo.mapper.AncientTreeInspectionMapper;
import com.example.demo.mapper.AncientTreeMapper;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.DroneMissionMapper;
import com.example.demo.mapper.EnvironmentRecordMapper;
import com.example.demo.mapper.EcologicalFollowupMapper;
import com.example.demo.mapper.WildlifeHabitatMapper;
import com.example.demo.service.AlarmService;
import com.example.demo.service.DataScopeService;
import com.example.demo.service.DroneMissionService;
import com.example.demo.service.EcologicalFollowupService;
import com.example.demo.service.FireWeatherService;
import com.example.demo.service.ForestMapService;
import com.example.demo.service.ForestOverviewService;
import com.example.demo.service.ForestZoneService;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 森林公园生态安全指挥台接口（受 /api/admin/** 的 ADMIN 权限保护）。
 *
 * 覆盖：总览 / SVG 地图 / 分区 / 火险事件（含详情与无人机派发）
 * / 无人机任务推进与火点确认 / 古树档案与巡护检查 / 栖息地
 * / 生态回访 / 气象 / 监测节点。
 *
 * 所有判定均为透明规则评分，不含 AI 描述。
 */
@RestController
@RequestMapping("/api/admin/forest")
public class AdminForestController {

    private final ForestOverviewService forestOverviewService;
    private final ForestMapService forestMapService;
    private final ForestZoneService forestZoneService;

    private final AlarmMapper alarmMapper;
    private final AlarmEventLogMapper alarmEventLogMapper;
    private final DeviceMapper deviceMapper;
    private final DroneMissionMapper droneMissionMapper;

    private final AncientTreeMapper ancientTreeMapper;
    private final AncientTreeInspectionMapper inspectionMapper;
    private final WildlifeHabitatMapper wildlifeHabitatMapper;
    private final EnvironmentRecordMapper environmentRecordMapper;
    private final EcologicalFollowupMapper ecologicalFollowupMapper;

    private final DroneMissionService droneMissionService;
    private final EcologicalFollowupService ecologicalFollowupService;
    private final FireWeatherService fireWeatherService;
    private final DataScopeService dataScopeService;

    public AdminForestController(ForestOverviewService forestOverviewService,
                                 ForestMapService forestMapService,
                                 ForestZoneService forestZoneService,
                                 AlarmMapper alarmMapper,
                                 AlarmEventLogMapper alarmEventLogMapper,
                                 DeviceMapper deviceMapper,
                                 DroneMissionMapper droneMissionMapper,
                                 AncientTreeMapper ancientTreeMapper,
                                 AncientTreeInspectionMapper inspectionMapper,
                                 WildlifeHabitatMapper wildlifeHabitatMapper,
                                 EnvironmentRecordMapper environmentRecordMapper,
                                 EcologicalFollowupMapper ecologicalFollowupMapper,
                                 DroneMissionService droneMissionService,
                                 EcologicalFollowupService ecologicalFollowupService,
                                 FireWeatherService fireWeatherService,
                                 DataScopeService dataScopeService) {
        this.forestOverviewService = forestOverviewService;
        this.forestMapService = forestMapService;
        this.forestZoneService = forestZoneService;
        this.alarmMapper = alarmMapper;
        this.alarmEventLogMapper = alarmEventLogMapper;
        this.deviceMapper = deviceMapper;
        this.droneMissionMapper = droneMissionMapper;
        this.ancientTreeMapper = ancientTreeMapper;
        this.inspectionMapper = inspectionMapper;
        this.wildlifeHabitatMapper = wildlifeHabitatMapper;
        this.environmentRecordMapper = environmentRecordMapper;
        this.ecologicalFollowupMapper = ecologicalFollowupMapper;
        this.droneMissionService = droneMissionService;
        this.ecologicalFollowupService = ecologicalFollowupService;
        this.fireWeatherService = fireWeatherService;
        this.dataScopeService = dataScopeService;
    }


    /* ==================== 总览 / 地图 / 分区 ==================== */

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return forestOverviewService.overview();
    }

    @GetMapping("/map")
    public Map<String, Object> map() {
        return forestMapService.map();
    }

    @GetMapping("/zones")
    public List<ForestZone> zones() {
        return forestZoneService.listZones();
    }


    /* ==================== 火险事件 ==================== */

    /**
     * 活动火险事件队列（scene_type=FOREST 且 recover_time IS NULL），
     * 按优先级总分降序。
     *
     * 可选筛选：zoneId（分区）、level（RED/ORANGE/YELLOW/LOW）。
     */
    @GetMapping("/events")
    public List<Alarm> events(
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) String level) {

        LambdaQueryWrapper<Alarm> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(Alarm::getSceneType, AlarmService.SCENE_FOREST)
                .isNull(Alarm::getRecoverTime);

        if (zoneId != null) {
            wrapper.eq(Alarm::getZoneId, zoneId);
        }

        if (StringUtils.hasText(level)) {
            wrapper.eq(Alarm::getPriorityLevel, level.trim().toUpperCase());
        }

        wrapper.orderByDesc(Alarm::getPriorityScore)
                .orderByDesc(Alarm::getAlarmTime);

        return alarmMapper.selectList(wrapper);
    }

    /**
     * 事件详情：事件本体 + 节点 + 处置时间线 + 生态回访 + 无人机任务。
     */
    @GetMapping("/events/{id}")
    public Map<String, Object> eventDetail(@PathVariable Long id) {

        Alarm alarm = requireAlarm(id);

        Device device = alarm.getDeviceId() == null
                ? null : deviceMapper.selectById(alarm.getDeviceId());

        List<AlarmEventLog> timeline = alarmEventLogMapper.selectList(
                new LambdaQueryWrapper<AlarmEventLog>()
                        .eq(AlarmEventLog::getAlarmId, id)
                        .orderByAsc(AlarmEventLog::getEventTime)
        );

        List<EcologicalFollowup> followups =
                ecologicalFollowupService.listByAlarm(id);

        List<DroneMission> missions = droneMissionMapper.selectList(
                new LambdaQueryWrapper<DroneMission>()
                        .eq(DroneMission::getAlarmId, id)
                        .orderByDesc(DroneMission::getId)
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("alarm", alarm);
        result.put("device", device);
        result.put("timeline", timeline);
        result.put("followups", followups);
        result.put("missions", missions);

        return result;
    }

    /**
     * 为火险事件派发无人机复核任务。
     *
     * POST /api/admin/forest/events/{id}/dispatch-drone
     * body: { "droneId": "DRONE-01" }
     */
    @PostMapping("/events/{id}/dispatch-drone")
    public Map<String, Object> dispatchDrone(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {

        Alarm alarm = requireAlarm(id);

        if (!AlarmService.SCENE_FOREST.equals(alarm.getSceneType())) {
            throw badRequest("仅森林火险事件支持无人机复核");
        }

        if (alarm.getRecoverTime() != null) {
            throw badRequest("事件环境已恢复，无需无人机复核");
        }

        long activeCount = droneMissionMapper.selectCount(
                new LambdaQueryWrapper<DroneMission>()
                        .eq(DroneMission::getAlarmId, id)
                        .ne(DroneMission::getState,
                                DroneMissionService.STATE_COMPLETED)
        );

        if (activeCount > 0) {
            throw badRequest("该事件已有在途无人机任务，请先完成再派发");
        }

        String droneId = body == null
                ? null : stringValue(body.get("droneId"));

        DroneMission mission =
                droneMissionService.dispatch(id, droneId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "无人机已派发复核");
        result.put("mission", droneMissionMapper.selectById(mission.getId()));
        return result;
    }


    /* ==================== 无人机任务 ==================== */

    /**
     * 推进无人机任务状态（DISPATCHED→EN_ROUTE→ON_SITE→RETURNED→COMPLETED）。
     */
    @PostMapping("/drones/missions/{id}/advance")
    public Map<String, Object> advanceDrone(@PathVariable Long id) {

        DroneMission mission = droneMissionService.advance(id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "无人机任务已推进");
        result.put("mission", droneMissionMapper.selectById(mission.getId()));
        return result;
    }

    /**
     * 到达现场后确认航拍结果。
     *
     * POST /api/admin/forest/drones/missions/{id}/confirm
     * body: { "confirmed": true, "note": "航拍确认明火" }
     *
     * confirmed=true → 事件 drone_confirmed=1，优先级强制 ≥95；
     * confirmed=false → 保留烟雾异常证据，等待人工复核。
     */
    @PostMapping("/drones/missions/{id}/confirm")
    public Map<String, Object> confirmDrone(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        boolean confirmed =
                body != null && truthy(body.get("confirmed"));

        String note = body == null
                ? null : stringValue(body.get("note"));

        DroneMission mission =
                droneMissionService.confirmFire(id, confirmed, note);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", confirmed ? "已确认火点" : "已记录未发现火点");
        result.put("mission", droneMissionMapper.selectById(mission.getId()));
        return result;
    }

    /**
     * 无人机任务列表（全部状态，按任务ID倒序）。
     */
    @GetMapping("/drones")
    public List<DroneMission> drones() {

        return droneMissionMapper.selectList(
                new LambdaQueryWrapper<DroneMission>()
                        .orderByDesc(DroneMission::getId)
        );
    }


    /* ==================== 古树档案 ==================== */

    @GetMapping("/trees")
    public List<AncientTree> trees(
            @RequestParam(required = false) Long zoneId) {

        LambdaQueryWrapper<AncientTree> wrapper = new LambdaQueryWrapper<>();

        if (zoneId != null) {
            wrapper.eq(AncientTree::getZoneId, zoneId);
        }

        wrapper.orderByAsc(AncientTree::getId);
        return ancientTreeMapper.selectList(wrapper);
    }

    @GetMapping("/trees/{id}")
    public Map<String, Object> treeDetail(@PathVariable Long id) {

        AncientTree tree = requireTree(id);

        List<AncientTreeInspection> inspections =
                inspectionMapper.selectList(
                        new LambdaQueryWrapper<AncientTreeInspection>()
                                .eq(AncientTreeInspection::getTreeId, id)
                                .orderByDesc(
                                        AncientTreeInspection::getInspectionDate)
                );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tree", tree);
        result.put("inspections", inspections);
        return result;
    }

    /**
     * 记录古树巡护检查，并同步更新古树当前健康状态。
     *
     * POST /api/admin/forest/trees/{id}/inspection
     * body: { "inspector": "张巡护", "healthState": "HEALTHY",
     *         "pestRisk": "无", "description": "长势良好" }
     */
    @PostMapping("/trees/{id}/inspection")
    public Map<String, Object> treeInspection(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {

        AncientTree tree = requireTree(id);

        AncientTreeInspection inspection = new AncientTreeInspection();
        inspection.setTreeId(tree.getId());
        inspection.setTreeCode(tree.getTreeCode());
        inspection.setInspectionDate(LocalDateTime.now());
        inspection.setInspector(currentOperator());
        inspection.setHealthState(body == null
                ? null : stringValue(body.get("healthState")));
        inspection.setPestRisk(body == null
                ? null : stringValue(body.get("pestRisk")));
        inspection.setDescription(body == null
                ? null : stringValue(body.get("description")));
        inspection.setCreatedAt(LocalDateTime.now());

        inspectionMapper.insert(inspection);

        if (StringUtils.hasText(inspection.getHealthState())) {
            tree.setHealthState(inspection.getHealthState());
            ancientTreeMapper.updateById(tree);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "巡护检查记录已保存");
        result.put("inspection",
                inspectionMapper.selectById(inspection.getId()));
        result.put("tree", ancientTreeMapper.selectById(tree.getId()));
        return result;
    }


    /* ==================== 野生动物栖息地 ==================== */

    @GetMapping("/habitats")
    public List<WildlifeHabitat> habitats(
            @RequestParam(required = false) Long zoneId) {

        LambdaQueryWrapper<WildlifeHabitat> wrapper =
                new LambdaQueryWrapper<>();

        if (zoneId != null) {
            wrapper.eq(WildlifeHabitat::getZoneId, zoneId);
        }

        wrapper.orderByAsc(WildlifeHabitat::getId);
        return wildlifeHabitatMapper.selectList(wrapper);
    }


    /* ==================== 生态回访 ==================== */

    /**
     * 生态回访任务列表。
     * 可选筛选：alarmId（事件）、state（PENDING/IN_PROGRESS/COMPLETED）。
     */
    @GetMapping("/followups")
    public List<EcologicalFollowup> followups(
            @RequestParam(required = false) Long alarmId,
            @RequestParam(required = false) String state) {

        List<EcologicalFollowup> list = alarmId != null
                ? ecologicalFollowupService.listByAlarm(alarmId)
                : ecologicalFollowupMapper.selectList(
                        new LambdaQueryWrapper<EcologicalFollowup>()
                                .orderByDesc(EcologicalFollowup::getId));

        if (!StringUtils.hasText(state)) {
            return list;
        }

        String filter = state.trim();
        return list.stream()
                .filter(f -> filter.equalsIgnoreCase(f.getState()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 开始生态回访：PENDING → IN_PROGRESS。
     */
    @PostMapping("/followups/{id}/start")
    public Map<String, Object> startFollowup(@PathVariable Long id) {

        EcologicalFollowup followup =
                ecologicalFollowupService.start(id, currentOperator());

        return followupResult("已开始生态回访", followup);
    }

    /**
     * 完成生态回访：IN_PROGRESS → COMPLETED。
     *
     * POST /api/admin/forest/followups/{id}/complete
     * body: { "note": "古树状态良好，无烧伤痕迹" }
     */
    @PostMapping("/followups/{id}/complete")
    public Map<String, Object> completeFollowup(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {

        String note = body == null
                ? null : stringValue(body.get("note"));

        EcologicalFollowup followup =
                ecologicalFollowupService.complete(id, currentOperator(), note);

        return followupResult("生态回访已完成", followup);
    }


    /* ==================== 气象 / 节点 ==================== */

    /**
     * 各分区火险气象（最新环境记录 + 气象评分）。
     */
    @GetMapping("/weather")
    public List<Map<String, Object>> weather() {

        List<Map<String, Object>> list = new ArrayList<>();

        for (ForestZone zone : forestZoneService.listZones()) {

            int score = fireWeatherService.score(zone.getId());

            EnvironmentRecord latest = environmentRecordMapper.selectOne(
                    new LambdaQueryWrapper<EnvironmentRecord>()
                            .eq(EnvironmentRecord::getZoneId, zone.getId())
                            .orderByDesc(EnvironmentRecord::getRecordTime)
                            .last("LIMIT 1")
            );

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("zoneId", zone.getId());
            item.put("zoneCode", zone.getZoneCode());
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

    /**
     * 森林监测节点（含最新云端判定状态），复用地图节点图层数据。
     */
    @GetMapping("/nodes")
    public List<Map<String, Object>> nodes() {

        Map<String, Object> map = forestMapService.map();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes =
                (List<Map<String, Object>>) map.get("nodes");

        return nodes;
    }


    /* ==================== 工具 ==================== */

    private Alarm requireAlarm(Long id) {

        Alarm alarm = alarmMapper.selectById(id);

        if (alarm == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "火险事件不存在"
            );
        }

        return alarm;
    }

    private AncientTree requireTree(Long id) {

        AncientTree tree = ancientTreeMapper.selectById(id);

        if (tree == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "古树档案不存在"
            );
        }

        return tree;
    }

    private String currentOperator() {
        return dataScopeService.getCurrentUser().getUsername();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean truthy(Object value) {

        if (value == null) {
            return false;
        }

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }

        return "true".equalsIgnoreCase(String.valueOf(value).trim());
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    private Map<String, Object> followupResult(
            String message,
            EcologicalFollowup followup) {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", message);
        result.put("followup",
                ecologicalFollowupService.listByAlarm(followup.getAlarmId()));
        return result;
    }
}
