package com.example.demo.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.demo.service.ForestEquipmentSelfTestService;
import com.example.demo.service.ForestPatrolSummaryService;
import com.example.demo.service.ForestRiskAnalysisService;
import com.example.demo.service.ForestWeatherService;
import com.example.demo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api/forest")
public class ForestController {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ForestPatrolSummaryService patrolSummaryService;

    @Autowired
    private ForestEquipmentSelfTestService equipmentSelfTestService;

    @Autowired
    private ForestWeatherService weatherService;

    @Autowired
    private ForestRiskAnalysisService riskAnalysisService;

    @GetMapping("/bootstrap")
    public Result<Map<String, Object>> bootstrap() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> parkInfo = jdbc.queryForMap("SELECT id,name,area,ancient_trees AS ancientTrees,visitors,fire_risk AS fireRisk FROM forest_park WHERE id=1");
        List<Map<String, Object>> sensors = jdbc.queryForList("SELECT id,name,zone,smoke,temperature,humidity,co,online,source,status,camera,map_x AS x,map_y AS y,latitude,longitude,updated_at AS updatedAt FROM forest_sensor_node ORDER BY id");
        List<Map<String, Object>> mapRows = jdbc.queryForList("SELECT id,geojson,west,south,east,north,center_lng AS centerLng,center_lat AS centerLat,zoom FROM forest_map WHERE id=1");
        Map<String, Object> map = mapRows.isEmpty() ? null : mapRows.get(0);
        double centerLng = map != null && map.get("centerLng") instanceof Number ? ((Number) map.get("centerLng")).doubleValue() : 119.281011D;
        double centerLat = map != null && map.get("centerLat") instanceof Number ? ((Number) map.get("centerLat")).doubleValue() : 26.161714D;
        Map<String, Object> weather = weatherService.current(centerLat, centerLng, sensors);
        Map<String, Object> riskAnalysis = riskAnalysisService.analyzeFireRisk(weather, sensors);
        parkInfo.put("fireRisk", riskAnalysis.get("level"));
        data.put("parkInfo", parkInfo);
        data.put("weather", weather);
        data.put("riskAnalysis", riskAnalysis);
        data.put("zones", jdbc.queryForList("SELECT id,name,type,risk,map_x AS x,map_y AS y,trees,latitude,longitude,geojson FROM forest_zone ORDER BY id"));
        data.put("sensors", sensors);
        data.put("drones", jdbc.queryForList("SELECT id,name,model,battery,status,location,thermal,operator_name AS operator,payloads,map_x AS x,map_y AS y,latitude,longitude,altitude,speed,satellites,link_quality AS linkQuality,eta_sec AS etaSec,phase,pm25,temperature,humidity,co,surface_temperature AS surfaceTemperature,wind_estimate AS windEstimate,updated_at AS updatedAt FROM forest_drone ORDER BY id"));
        data.put("patrols", jdbc.queryForList("SELECT m.id,m.name,m.route,m.ranger,m.drone,m.mode,m.status,m.progress,m.plan_time AS planTime,m.coverage,m.images,m.hotspots,m.samples,m.eta_sec AS etaSec,m.max_temperature AS maxTemperature,m.report,m.summary_source AS summarySource,m.ranger_latitude AS rangerLatitude,m.ranger_longitude AS rangerLongitude,m.drone_latitude AS droneLatitude,m.drone_longitude AS droneLongitude,m.create_time AS createTime,m.update_time AS updateTime,l.incident_id AS incidentId FROM drone_mission m LEFT JOIN drone_mission_incident l ON l.mission_id=m.id ORDER BY m.create_time DESC,m.id DESC"));
        data.put("incidents", jdbc.queryForList("SELECT id,event_time AS time,zone,level,source,status,smoke,temperature,confidence,analysis_model AS analysisModel,analysis_detail AS analysisDetail,reason,result,ranger FROM forest_incident ORDER BY event_time DESC"));
        data.put("smokeHistory", trend("smoke"));
        data.put("temperatureHistory", trend("temperature"));
        data.put("humidityHistory", trend("humidity"));
        data.put("map", map);
        data.put("rangers", jdbc.queryForList("SELECT ranger,latitude,longitude,altitude,accuracy,updated_at AS updatedAt FROM ranger_position ORDER BY ranger"));
        return Result.ok(data);
    }

    private List<Map<String, Object>> trend(String metric) {
        return jdbc.queryForList("SELECT point_label AS label,value FROM forest_trend_point WHERE metric=? ORDER BY sort_no", metric);
    }

    /**
     * 节点历史趋势。GT-01 对应华为云真机 device_id=1，烟雾读取 smoke_record
     * 的最近采样；真机未上报的温湿度以及其他仿真节点仍返回仿真趋势，并明确标识来源。
     */
    @GetMapping("/sensors/{id}/history")
    public Result<Map<String, Object>> sensorHistory(
            @PathVariable String id,
            @RequestParam(defaultValue = "smoke") String metric,
            @RequestParam(defaultValue = "120") Integer limit) {
        if (!List.of("smoke", "temperature", "humidity").contains(metric)) {
            return Result.fail("不支持的历史指标");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> points;
        boolean real = "GT-01".equals(id) && "smoke".equals(metric);
        if (real) {
            int size = limit == null ? 120 : Math.max(10, Math.min(limit, 300));
            points = jdbc.queryForList(
                    "SELECT DATE_FORMAT(collect_time,'%H:%i:%s') AS label," +
                            " smoke_concentration AS value,collect_time AS collectedAt" +
                            " FROM smoke_record WHERE device_id=1" +
                            " ORDER BY collect_time DESC LIMIT " + size);
            Collections.reverse(points);
        } else {
            points = trend(metric);
        }
        data.put("nodeId", id);
        data.put("metric", metric);
        data.put("source", real ? "real" : "simulated");
        data.put("sourceText", real ? "华为云真机数据库" : "平台仿真历史");
        data.put("points", points);
        data.put("updatedAt", points.isEmpty() ? null : points.get(points.size() - 1).get("collectedAt"));
        return Result.ok(data);
    }

    @PostMapping("/missions")
    @Transactional
    public Result<Map<String, Object>> createMission(@RequestBody Map<String, Object> body) {
        String id = text(body.get("id"));
        if (id.isEmpty()) id = "PATROL-" + System.currentTimeMillis();
        String mode = "manual";
        String incidentId = text(body.get("incidentId"));
        if (!incidentId.isEmpty()) {
            Integer activeLinked = jdbc.queryForObject("SELECT COUNT(*) FROM drone_mission_incident l JOIN drone_mission m ON m.id=l.mission_id WHERE l.incident_id=? AND m.status<>'已完成'", Integer.class, incidentId);
            if (activeLinked != null && activeLinked > 0) return Result.fail("该警情已有执行中的核查任务");
        }
        String ranger = text(body.get("ranger"));
        jdbc.update("INSERT INTO drone_mission(id,name,route,ranger,drone,mode,status,progress,plan_time,coverage,images,hotspots,samples,eta_sec) VALUES(?,?,?,?,?,?,'待执行',0,?,'待计算',0,0,0,0)",
                id, text(body.get("name")), text(body.get("route")), ranger, text(body.get("drone")), mode, text(body.get("planTime")));
        if (!incidentId.isEmpty()) jdbc.update("INSERT INTO drone_mission_incident(mission_id,incident_id) VALUES(?,?)", id, incidentId);
        return Result.ok(jdbc.queryForMap("SELECT m.id,m.name,m.route,m.ranger,m.drone,m.mode,m.status,m.progress,m.plan_time AS planTime,m.coverage,m.images,m.hotspots,m.samples,m.eta_sec AS etaSec,l.incident_id AS incidentId FROM drone_mission m LEFT JOIN drone_mission_incident l ON l.mission_id=m.id WHERE m.id=?", id));
    }

    @PostMapping("/missions/{id}/start")
    @Transactional
    public Result<String> startMission(@PathVariable String id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT m.drone,m.ranger,m.status,l.incident_id AS incidentId FROM drone_mission m LEFT JOIN drone_mission_incident l ON l.mission_id=m.id WHERE m.id=?", id);
        if (rows.isEmpty()) return Result.fail("巡护任务不存在");
        Map<String, Object> mission = rows.get(0);
        String ranger = text(body.get("ranger"));
        String assignedRanger = text(mission.get("ranger"));
        boolean allRangers = "全体护林员".equals(assignedRanger);
        if (ranger.isEmpty() || (!allRangers && !ranger.equals(assignedRanger))) return Result.fail("只有该任务的处置护林员可以开始巡护");
        String status = text(mission.get("status"));
        boolean dailyPatrol = text(mission.get("incidentId")).isEmpty();
        if (dailyPatrol) {
            if (!List.of("待执行", "已接收", "前往现场").contains(status)) return Result.fail("当前日常巡护任务状态不能开始");
        } else if (!"前往现场".equals(status)) {
            return Result.fail("警情核查任务请先完成接取和到场流程");
        }
        String droneName = text(mission.get("drone"));
        jdbc.update("UPDATE drone_mission SET mode='manual',status='手动飞行',progress=0,eta_sec=0,report=NULL WHERE id=?", id);
        jdbc.update("UPDATE forest_drone SET status='flying',phase='手动飞行 · 悬停观察',eta_sec=0,location='起降坪上空',map_x=49,map_y=79,latitude=26.154619,longitude=119.285990,altitude=60,speed=3.2 WHERE name=?", droneName);
        return Result.ok("手动巡护已开始");
    }

    @PutMapping("/missions/{id}/status")
    @Transactional
    public Result<Map<String, Object>> updateMissionStatus(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String status = text(body.get("status"));
        if (!List.of("已接收", "前往现场").contains(status)) return Result.fail("不支持的任务状态");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM drone_mission WHERE id=?", Integer.class, id);
        if (count == null || count == 0) return Result.fail("巡护任务不存在");
        String ranger = text(body.get("ranger"));
        String assignedRanger = jdbc.queryForObject("SELECT ranger FROM drone_mission WHERE id=?", String.class, id);
        if ("已接收".equals(status)) {
            if (ranger.isEmpty()) return Result.fail("接取人不能为空");
            int claimed = jdbc.update("UPDATE drone_mission SET status='已接收',ranger=? WHERE id=? AND status='待执行' AND (ranger='' OR ranger IS NULL OR ranger=?)", ranger, id, ranger);
            if (claimed == 0 && !ranger.equals(assignedRanger)) return Result.fail("任务已被其他护林员接取");
            jdbc.update("UPDATE forest_incident i JOIN drone_mission_incident l ON l.incident_id=i.id SET i.ranger=? WHERE l.mission_id=?", ranger, id);
        } else {
            if (ranger.isEmpty() || assignedRanger == null || !assignedRanger.equals(ranger)) return Result.fail("只有接取该任务的护林员可以更新进度");
            jdbc.update("UPDATE drone_mission SET status=? WHERE id=? AND ranger=? AND status<>'已完成'", status, id, ranger);
        }
        return Result.ok(jdbc.queryForMap("SELECT m.id,m.name,m.route,m.ranger,m.drone,m.mode,m.status,m.progress,m.plan_time AS planTime,m.coverage,m.images,m.hotspots,m.samples,l.incident_id AS incidentId FROM drone_mission m LEFT JOIN drone_mission_incident l ON l.mission_id=m.id WHERE m.id=?", id));
    }

    @PostMapping("/missions/{id}/telemetry")
    @Transactional
    public Result<String> saveTelemetry(@PathVariable String id, @RequestBody Map<String, Object> b) {
        String droneId = text(b.get("droneId"));
        jdbc.update("UPDATE drone_mission SET mode='manual',status='手动飞行',progress=0,eta_sec=0,coverage=?,images=?,hotspots=?,samples=? WHERE id=?",
                text(b.get("coverage")), integer(b.get("images")), integer(b.get("hotspots")), integer(b.get("samples")), id);
        jdbc.update("UPDATE forest_drone SET battery=?,status=?,location=?,map_x=?,map_y=?,latitude=?,longitude=?,altitude=?,speed=?,satellites=?,link_quality=?,eta_sec=?,phase=?,pm25=?,temperature=?,humidity=?,co=?,surface_temperature=?,wind_estimate=?,updated_at=NOW() WHERE id=?",
                decimal(b.get("battery")), text(b.get("droneStatus")), text(b.get("location")), decimal(b.get("x")), decimal(b.get("y")), decimal(b.get("latitude")), decimal(b.get("longitude")), decimal(b.get("altitude")), decimal(b.get("speed")), integer(b.get("satellites")), integer(b.get("linkQuality")), integer(b.get("etaSec")), text(b.get("phase")), nullableDecimal(b.get("pm25")), nullableDecimal(b.get("temperature")), nullableDecimal(b.get("humidity")), nullableDecimal(b.get("co")), nullableDecimal(b.get("surfaceTemperature")), nullableDecimal(b.get("windEstimate")), droneId);
        jdbc.update("INSERT INTO drone_telemetry(mission_id,drone_id,latitude,longitude,altitude,speed,battery,satellites,link_quality,pm25,temperature,humidity,co,surface_temperature,wind_estimate) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, droneId, decimal(b.get("latitude")), decimal(b.get("longitude")), decimal(b.get("altitude")), decimal(b.get("speed")), decimal(b.get("battery")), integer(b.get("satellites")), integer(b.get("linkQuality")), nullableDecimal(b.get("pm25")), nullableDecimal(b.get("temperature")), nullableDecimal(b.get("humidity")), nullableDecimal(b.get("co")), nullableDecimal(b.get("surfaceTemperature")), nullableDecimal(b.get("windEstimate")));
        return Result.ok("遥测已保存");
    }

    @PostMapping("/missions/{id}/complete")
    @Transactional
    public Result<Map<String, Object>> completeMission(@PathVariable String id, @RequestBody Map<String, Object> b) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,name,route,ranger,drone FROM drone_mission WHERE id=?", id);
        if (rows.isEmpty()) return Result.fail("巡护任务不存在");
        Map<String, Object> mission = rows.get(0);
        String ranger = text(b.get("ranger"));
        String assignedRanger = text(mission.get("ranger"));
        if (ranger.isEmpty() || (!"全体护林员".equals(assignedRanger) && !ranger.equals(assignedRanger))) return Result.fail("只有该任务的处置护林员可以上报巡护结果");
        String droneId = text(b.get("droneId"));
        if (droneId.isEmpty()) {
            List<String> droneIds = jdbc.query("SELECT id FROM forest_drone WHERE name=? LIMIT 1", (rs, n) -> rs.getString(1), text(mission.get("drone")));
            if (!droneIds.isEmpty()) droneId = droneIds.get(0);
        }

        Integer existingTelemetry = jdbc.queryForObject("SELECT COUNT(*) FROM drone_telemetry WHERE mission_id=?", Integer.class, id);
        PatrolSimulation simulation = existingTelemetry != null && existingTelemetry > 0
                ? analyzePersistedTelemetry(id)
                : simulatePatrolTelemetry(id, droneId, nullableDecimal(b.get("droneLongitude")), nullableDecimal(b.get("droneLatitude")));
        String coverage = text(b.get("coverage"));
        if (coverage.isEmpty()) coverage = "0.4 km²";
        int images = Math.max(0, integer(b.get("images")));
        int hotspots = simulation.maxSurfaceTemperature >= 55D ? 1 : 0;
        String fallback = buildRuleSummary(coverage, simulation, hotspots);
        String facts = "任务：" + text(mission.get("name")) + "；路线：" + text(mission.get("route"))
                + "；覆盖面积：" + coverage + "；遥测组数：" + simulation.count
                + "；PM2.5平均/峰值：" + simulation.avgPm25 + "/" + simulation.maxPm25 + " μg/m³"
                + "；CO平均/峰值：" + simulation.avgCo + "/" + simulation.maxCo + " ppm"
                + "；环境温度峰值：" + simulation.maxTemperature + "℃；地表温度峰值：" + simulation.maxSurfaceTemperature + "℃"
                + "；热成像热点：" + hotspots + "处；影像：" + images + "张；多源同步越限：" + (hotspots > 0 ? "是" : "否") + "。";
        ForestPatrolSummaryService.SummaryResult summary = patrolSummaryService.summarize(facts, fallback);

        jdbc.update("UPDATE drone_mission SET status='已完成',progress=100,eta_sec=0,coverage=?,images=?,hotspots=?,samples=?,max_temperature=?,report=?,summary_source=?,ranger_latitude=?,ranger_longitude=?,drone_latitude=?,drone_longitude=? WHERE id=?",
                coverage, images, hotspots, simulation.count, simulation.maxSurfaceTemperature, summary.report, summary.source, nullableDecimal(b.get("rangerLatitude")), nullableDecimal(b.get("rangerLongitude")), simulation.lastLatitude, simulation.lastLongitude, id);
        jdbc.update("UPDATE forest_drone SET status='idle',phase='数据已归档',location='游客中心起降坪',altitude=0,speed=0,eta_sec=0,map_x=49,map_y=79,latitude=26.154619,longitude=119.285990,updated_at=NOW() WHERE id=?", droneId);
        jdbc.update("UPDATE forest_incident i JOIN drone_mission_incident l ON l.incident_id=i.id SET i.status='processing',i.result=? WHERE l.mission_id=? AND i.status='verifying'", "无人机核查数据已返回并归档，等待护林员上报最终处置结果", id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "已完成");
        data.put("progress", 100);
        data.put("coverage", coverage);
        data.put("images", images);
        data.put("hotspots", hotspots);
        data.put("samples", simulation.count);
        data.put("maxTemperature", simulation.maxSurfaceTemperature);
        data.put("report", summary.report);
        data.put("summarySource", summary.source);
        data.put("droneLatitude", simulation.lastLatitude);
        data.put("droneLongitude", simulation.lastLongitude);
        data.put("telemetryCount", simulation.count);
        return Result.ok(data);
    }

    @GetMapping("/missions/{id}/detail")
    public Result<Map<String, Object>> missionDetail(@PathVariable String id) {
        List<Map<String, Object>> missions = jdbc.queryForList("SELECT m.id,m.name,m.route,m.ranger,m.drone,m.mode,m.status,m.progress,m.plan_time AS planTime,m.coverage,m.images,m.hotspots,m.samples,m.max_temperature AS maxTemperature,m.report,m.summary_source AS summarySource,m.ranger_latitude AS rangerLatitude,m.ranger_longitude AS rangerLongitude,m.drone_latitude AS droneLatitude,m.drone_longitude AS droneLongitude,m.create_time AS createTime,m.update_time AS updateTime,l.incident_id AS incidentId FROM drone_mission m LEFT JOIN drone_mission_incident l ON l.mission_id=m.id WHERE m.id=?", id);
        if (missions.isEmpty()) return Result.fail("巡护任务不存在");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mission", missions.get(0));
        data.put("photos", jdbc.queryForList("SELECT id,mission_id AS missionId,category,zone_name AS zoneName,tree_code AS treeCode,tags,image_data AS imageData,object_url AS objectUrl,latitude,longitude,surface_temperature AS surfaceTemperature,note,DATE_FORMAT(captured_at,'%Y-%m-%d %H:%i:%s') AS capturedAt FROM drone_mission_photo WHERE mission_id=? ORDER BY captured_at DESC,id DESC", id));
        data.put("telemetry", jdbc.queryForList("SELECT latitude,longitude,altitude,speed,battery,pm25,temperature,humidity,co,surface_temperature AS surfaceTemperature,wind_estimate AS windEstimate,collect_time AS collectTime FROM drone_telemetry WHERE mission_id=? ORDER BY collect_time DESC LIMIT 20", id));
        return Result.ok(data);
    }

    @PostMapping("/missions/{id}/photos")
    public Result<Map<String, Object>> addMissionPhoto(@PathVariable String id, @RequestBody Map<String, Object> b) {
        List<String> assigned = jdbc.query("SELECT ranger FROM drone_mission WHERE id=?", (rs, n) -> rs.getString(1), id);
        if (assigned.isEmpty()) return Result.fail("巡护任务不存在");
        String ranger = text(b.get("ranger"));
        if (ranger.isEmpty() || !ranger.equals(assigned.get(0))) return Result.fail("只有接取该任务的护林员可以上报现场照片");
        Integer missionCount = jdbc.queryForObject("SELECT COUNT(*) FROM drone_mission WHERE id=?", Integer.class, id);
        if (missionCount == null || missionCount == 0) return Result.fail("巡护任务不存在");
        if (text(b.get("imageData")).isEmpty() && text(b.get("objectUrl")).isEmpty()) return Result.fail("照片内容或对象存储地址不能为空");
        if (text(b.get("category")).isEmpty()) return Result.fail("影像分类不能为空");
        jdbc.update("INSERT INTO drone_mission_photo(mission_id,category,zone_name,tree_code,tags,image_data,object_url,latitude,longitude,surface_temperature,note,captured_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,NOW())",
                id, text(b.get("category")), nullableText(b.get("zoneName")), nullableText(b.get("treeCode")), nullableText(b.get("tags")), nullableText(b.get("imageData")), nullableText(b.get("objectUrl")), nullableDecimal(b.get("latitude")), nullableDecimal(b.get("longitude")), nullableDecimal(b.get("surfaceTemperature")), nullableText(b.get("note")));
        Long photoId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return Result.ok(jdbc.queryForMap("SELECT id,mission_id AS missionId,category,zone_name AS zoneName,tree_code AS treeCode,tags,image_data AS imageData,object_url AS objectUrl,latitude,longitude,surface_temperature AS surfaceTemperature,note,DATE_FORMAT(captured_at,'%Y-%m-%d %H:%i:%s') AS capturedAt FROM drone_mission_photo WHERE id=?", photoId));
    }

    @GetMapping("/map")
    public Result<Map<String, Object>> getMap() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,geojson,west,south,east,north,center_lng AS centerLng,center_lat AS centerLat,zoom FROM forest_map WHERE id=1");
        return Result.ok(rows.isEmpty() ? null : rows.get(0));
    }

    @PutMapping("/map")
    public Result<String> saveMap(@RequestBody Map<String, Object> b) {
        jdbc.update("INSERT INTO forest_map(id,geojson,west,south,east,north) VALUES(1,?,?,?,?,?) ON DUPLICATE KEY UPDATE geojson=VALUES(geojson),west=VALUES(west),south=VALUES(south),east=VALUES(east),north=VALUES(north)",
                nullableText(b.get("geojson")), decimal(b.get("west")), decimal(b.get("south")), decimal(b.get("east")), decimal(b.get("north")));
        return Result.ok("地图已保存");
    }

    @PostMapping("/map/view")
    public Result<String> saveMapView(@RequestBody Map<String, Object> b) {
        jdbc.update("UPDATE forest_map SET center_lng=?,center_lat=?,zoom=? WHERE id=1",
                nullableDecimal(b.get("centerLng")), nullableDecimal(b.get("centerLat")), b.get("zoom") == null || text(b.get("zoom")).isEmpty() ? null : integer(b.get("zoom")));
        return Result.ok("地图视图已保存");
    }

    @PutMapping("/zones/{id}")
    @Transactional
    public Result<String> updateZone(@PathVariable String id, @RequestBody Map<String, Object> b) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT name FROM forest_zone WHERE id=?", id);
        if (rows.isEmpty()) return Result.fail("分区不存在或已删除");
        String oldName = text(rows.get(0).get("name"));
        String newName = text(b.get("name"));
        jdbc.update("UPDATE forest_zone SET name=?,type=?,risk=?,trees=?,geojson=?,latitude=?,longitude=? WHERE id=?",
                newName, text(b.get("type")), text(b.get("risk")), integer(b.get("trees")),
                nullableText(b.get("geojson")), nullableDecimal(b.get("latitude")), nullableDecimal(b.get("longitude")), id);
        if (!oldName.equals(newName)) {
            jdbc.update("UPDATE forest_sensor_node SET zone=? WHERE zone=?", newName, oldName);
            jdbc.update("UPDATE forest_incident SET zone=? WHERE zone=?", newName, oldName);
        }
        return Result.ok("分区已保存");
    }

    @PostMapping("/zones")
    public Result<String> createZone(@RequestBody Map<String, Object> b) {
        Long nextId = jdbc.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM forest_zone", Long.class);
        jdbc.update("INSERT INTO forest_zone(id,name,type,risk,map_x,map_y,trees,latitude,longitude,geojson) VALUES(?,?,?,?,?,?,?,?,?,?)",
                nextId, text(b.get("name")), text(b.get("type")), text(b.get("risk")), 50D, 50D,
                integer(b.get("trees")), nullableDecimal(b.get("latitude")), nullableDecimal(b.get("longitude")), nullableText(b.get("geojson")));
        return Result.ok("分区已创建");
    }

    @DeleteMapping("/zones/{id}")
    @Transactional
    public Result<String> deleteZone(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT name FROM forest_zone WHERE id=?", id);
        if (rows.isEmpty()) return Result.fail("分区不存在或已删除");
        String zoneName = text(rows.get(0).get("name"));
        Integer activeIncidents = jdbc.queryForObject(
                "SELECT COUNT(*) FROM forest_incident WHERE zone=? AND status<>'closed'", Integer.class, zoneName);
        if (activeIncidents != null && activeIncidents > 0) return Result.fail("该分区存在未结案警情，请结案后再删除");
        jdbc.update("UPDATE forest_sensor_node SET zone='未分区' WHERE zone=?", zoneName);
        jdbc.update("DELETE FROM forest_zone WHERE id=?", id);
        return Result.ok("分区已删除");
    }

    @PostMapping("/rangers/position")
    public Result<String> reportRangerPosition(@RequestBody Map<String, Object> b) {
        jdbc.update("INSERT INTO ranger_position(ranger,latitude,longitude,altitude,accuracy) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE latitude=VALUES(latitude),longitude=VALUES(longitude),altitude=VALUES(altitude),accuracy=VALUES(accuracy)",
                text(b.get("ranger")), decimal(b.get("latitude")), decimal(b.get("longitude")), nullableDecimal(b.get("altitude")), nullableDecimal(b.get("accuracy")));
        return Result.ok("护林员位置已保存");
    }

    @PutMapping("/incidents/{id}")
    @Transactional
    public Result<String> updateIncident(@PathVariable String id, @RequestBody Map<String, Object> b) {
        List<Map<String, Object>> before = jdbc.queryForList("SELECT level,source FROM forest_incident WHERE id=?", id);
        jdbc.update("UPDATE forest_incident SET status=?,result=?,ranger=? WHERE id=?", text(b.get("status")), text(b.get("result")), text(b.get("ranger")), id);
        if (!before.isEmpty() && "演示模拟".equals(text(before.get(0).get("source"))) && "closed".equals(text(b.get("status")))) {
            jdbc.update("UPDATE forest_drone d JOIN drone_mission m ON m.drone=d.name JOIN drone_mission_incident l ON l.mission_id=m.id SET d.status='idle',d.phase='演示结束 · 地面待命',d.altitude=0,d.speed=0,d.eta_sec=0 WHERE l.incident_id=?", id);
            jdbc.update("UPDATE drone_mission m JOIN drone_mission_incident l ON l.mission_id=m.id SET m.status='已完成',m.progress=100,m.eta_sec=0,m.report='演示结束，处置流程已归档' WHERE l.incident_id=?", id);
        }
        if (!before.isEmpty() && "三级".equals(text(before.get(0).get("level"))) && "closed".equals(text(b.get("status")))) {
            Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM forest_incident WHERE level='三级' AND status<>'closed'", Integer.class);
            if (active == null || active == 0) {
                jdbc.update("INSERT INTO forest_broadcast(level,title,message,area,exit_name,publish_time) VALUES('normal','全园疏散警报解除','三级警情已完成现场核查和处置，全园清场状态解除。游客请按照工作人员指引有序活动，临时封闭区域以现场标识为准。','全园','请服从现场护林员指引',NOW())");
            }
        }
        return Result.ok("警情已保存");
    }

    @PostMapping("/incidents")
    @Transactional
    public Result<Map<String, Object>> createIncident(@RequestBody Map<String, Object> b) {
        String id = "FIRE-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String zone = text(b.get("zone"));
        List<Map<String, Object>> sensors = jdbc.queryForList("SELECT id,zone,smoke,temperature,humidity,co,online,status FROM forest_sensor_node WHERE online=1 ORDER BY updated_at DESC");
        Map<String, Object> sensor = new LinkedHashMap<>();
        for (Map<String, Object> row : sensors) if (zone.equals(text(row.get("zone")))) { sensor = row; break; }
        List<Map<String, Object>> maps = jdbc.queryForList("SELECT center_lng AS centerLng,center_lat AS centerLat FROM forest_map WHERE id=1");
        Map<String, Object> map = maps.isEmpty() ? new LinkedHashMap<>() : maps.get(0);
        double longitude = map.get("centerLng") instanceof Number ? ((Number) map.get("centerLng")).doubleValue() : 119.281011D;
        double latitude = map.get("centerLat") instanceof Number ? ((Number) map.get("centerLat")).doubleValue() : 26.161714D;
        Map<String, Object> weather = weatherService.current(latitude, longitude, sensors);
        Map<String, Object> fireRisk = riskAnalysisService.analyzeFireRisk(weather, sensors);
        Map<String, Object> analysis = riskAnalysisService.analyzeIncident(b, sensor, weather, integer(fireRisk.get("score")));
        jdbc.update("INSERT INTO forest_incident(id,event_time,zone,level,source,status,smoke,temperature,confidence,analysis_model,analysis_detail,reason,result,ranger) VALUES(?,NOW(),?,?,?,?,?,?,?,?,?,?,?,?)",
                id, zone, text(analysis.get("level")), text(b.get("source")), text(b.get("status")), nullableDecimal(analysis.get("smoke")), nullableDecimal(analysis.get("temperature")), integer(analysis.get("score")), text(analysis.get("algorithm")), text(analysis.get("detail")), text(analysis.get("reason")), text(b.get("result")), text(b.get("ranger")));
        if ("三级".equals(text(analysis.get("level")))) activateParkEmergency(id, zone);
        return Result.ok(jdbc.queryForMap("SELECT id,event_time AS time,zone,level,source,status,smoke,temperature,confidence,analysis_model AS analysisModel,analysis_detail AS analysisDetail,reason,result,ranger FROM forest_incident WHERE id=?", id));
    }

    /** 课程演示入口：构造可解释的多源参数，并继续使用正式融合模型完成分级。 */
    @PostMapping("/incidents/simulate")
    @Transactional
    public Result<Map<String, Object>> simulateIncident(@RequestBody Map<String, Object> body) {
        String requestedLevel = text(body.get("level"));
        if (!List.of("一级", "二级", "三级").contains(requestedLevel)) return Result.fail("请选择一级、二级或三级警情");

        List<Map<String, Object>> zoneRows = jdbc.queryForList("SELECT name FROM forest_zone ORDER BY CASE WHEN type LIKE '%古树%' THEN 0 ELSE 1 END,id");
        if (zoneRows.isEmpty()) return Result.fail("请先建立森林分区");
        String requestedZone = text(body.get("zone"));
        String zone = requestedZone.isEmpty() ? text(zoneRows.get(0).get("name")) : requestedZone;

        Map<String, Object> sample = new LinkedHashMap<>();
        if ("一级".equals(requestedLevel)) {
            sample.put("smoke", 38D); sample.put("co", 3D); sample.put("temperature", 30D); sample.put("humidity", 55D); sample.put("confidence", 35D);
            sample.put("reason", "演示场景：烟雾短时轻度升高，其他指标基本正常，需摄像头复查环境干扰");
        } else if ("二级".equals(requestedLevel)) {
            sample.put("smoke", 70D); sample.put("co", 18D); sample.put("temperature", 36D); sample.put("humidity", 34D); sample.put("confidence", 65D);
            sample.put("reason", "演示场景：烟雾、CO与温度同步升高，存在较明显火情可能");
        } else {
            sample.put("smoke", 100D); sample.put("co", 35D); sample.put("temperature", 45D); sample.put("humidity", 20D); sample.put("confidence", 95D);
            sample.put("reason", "演示场景：高浓度烟雾、CO、高温热点与视觉烟火特征同时出现");
        }

        List<Map<String, Object>> sensors = jdbc.queryForList("SELECT id,zone,smoke,temperature,humidity,co,online,status FROM forest_sensor_node WHERE online=1 ORDER BY updated_at DESC");
        List<Map<String, Object>> maps = jdbc.queryForList("SELECT center_lng AS centerLng,center_lat AS centerLat FROM forest_map WHERE id=1");
        Map<String, Object> map = maps.isEmpty() ? new LinkedHashMap<>() : maps.get(0);
        double longitude = map.get("centerLng") instanceof Number ? ((Number) map.get("centerLng")).doubleValue() : 119.281011D;
        double latitude = map.get("centerLat") instanceof Number ? ((Number) map.get("centerLat")).doubleValue() : 26.161714D;
        Map<String, Object> weather = weatherService.current(latitude, longitude, sensors);
        int backgroundScore = integer(riskAnalysisService.analyzeFireRisk(weather, sensors).get("score"));
        Map<String, Object> analysis = riskAnalysisService.analyzeIncident(sample, new LinkedHashMap<>(), weather, backgroundScore);
        if (!requestedLevel.equals(text(analysis.get("level")))) return Result.fail("演示参数未得到预期等级，请检查融合模型配置");

        String id = "DEMO-FIRE-" + System.currentTimeMillis();
        String status = "三级".equals(requestedLevel) ? "verifying" : "pending";
        jdbc.update("INSERT INTO forest_incident(id,event_time,zone,level,source,status,smoke,temperature,confidence,analysis_model,analysis_detail,reason,result,ranger) VALUES(?,NOW(),?,?,'演示模拟',?,?,?,?,?,?,?,?,?)",
                id, zone, text(analysis.get("level")), status, nullableDecimal(analysis.get("smoke")), nullableDecimal(analysis.get("temperature")), integer(analysis.get("score")), text(analysis.get("algorithm")), text(analysis.get("detail")), text(analysis.get("reason")), "", "");
        if ("三级".equals(requestedLevel)) activateParkEmergency(id, zone);
        return Result.ok(jdbc.queryForMap("SELECT id,event_time AS time,zone,level,source,status,smoke,temperature,confidence,analysis_model AS analysisModel,analysis_detail AS analysisDetail,reason,result,ranger FROM forest_incident WHERE id=?", id));
    }

    /**
     * 三级警情联动：全园清场广播 + 全体护林员共同处置任务。
     * 不经过抢单或个人接取，任一在岗护林员均可启动无人机核查和上报结果。
     */
    private void activateParkEmergency(String incidentId, String zone) {
        String title = "紧急清场：三级火情 " + incidentId;
        Integer broadcastExists = jdbc.queryForObject("SELECT COUNT(*) FROM forest_broadcast WHERE title=?", Integer.class, title);
        if (broadcastExists == null || broadcastExists == 0) {
            jdbc.update("INSERT INTO forest_broadcast(level,title,message,area,exit_name,publish_time) VALUES('danger',?,'公园内发生三级火情，现启动全园紧急清场。所有游客请立即停止游览，沿疏散步道前往最近安全出口或游客中心集结点，严禁靠近火情区域。','全园所有分区及开放步道','游客中心集结点 / 北门安全出口 / 东门停车场',NOW())", title);
        }

        jdbc.update("UPDATE forest_incident SET ranger='全体护林员' WHERE id=? AND status<>'closed'", incidentId);

        Integer linked = jdbc.queryForObject("SELECT COUNT(*) FROM drone_mission_incident WHERE incident_id=?", Integer.class, incidentId);
        if (linked != null && linked > 0) return;
        List<Map<String, Object>> available = jdbc.queryForList(
                "SELECT d.id,d.name FROM forest_drone d WHERE d.status='idle'" +
                        " AND NOT EXISTS(SELECT 1 FROM drone_mission m WHERE m.drone=d.name AND m.status<>'已完成')" +
                        " ORDER BY d.battery DESC LIMIT 1");
        if (available.isEmpty()) return;
        String missionId = "EMERGENCY-" + System.currentTimeMillis();
        String droneName = text(available.get(0).get("name"));
        jdbc.update("INSERT INTO drone_mission(id,name,route,ranger,drone,mode,status,progress,plan_time,coverage,images,hotspots,samples,eta_sec) VALUES(?,?,?,'全体护林员',?,'manual','前往现场',0,'立即执行','待计算',0,0,0,0)",
                missionId, "三级火情紧急核查：" + zone, "全园紧急清场巡查 → " + zone + " → 周边扩散方向 → 返回起降点", droneName);
        jdbc.update("INSERT INTO drone_mission_incident(mission_id,incident_id) VALUES(?,?)", missionId, incidentId);
    }

    /** 应用启动后补齐数据库中尚未联动的未结案三级警情，方便已有演示数据直接生效。 */
    @EventListener(ApplicationReadyEvent.class)
    public void reconcileLevelThreeEmergencies() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,zone FROM forest_incident WHERE level='三级' AND status<>'closed'");
        for (Map<String, Object> row : rows) activateParkEmergency(text(row.get("id")), text(row.get("zone")));
    }

    @PostMapping("/sensors/{id}/reading")
    public Result<String> updateSensor(@PathVariable String id, @RequestBody Map<String, Object> b) {
        jdbc.update("UPDATE forest_sensor_node SET smoke=?,temperature=?,humidity=?,co=?,status=?,online=1,updated_at=NOW() WHERE id=?",
                nullableDecimal(b.get("smoke")), nullableDecimal(b.get("temperature")), nullableDecimal(b.get("humidity")), nullableDecimal(b.get("co")), text(b.get("status")), id);
        return Result.ok("传感数据已保存");
    }

    @PostMapping("/broadcast")
    public Result<String> publishBroadcast(@RequestBody Map<String, Object> b) {
        jdbc.update("INSERT INTO forest_broadcast(level,title,message,area,exit_name,publish_time) VALUES(?,?,?,?,?,NOW())", text(b.get("level")), text(b.get("title")), text(b.get("message")), text(b.get("area")), text(b.get("exit")));
        return Result.ok("预警已保存");
    }

    @GetMapping("/broadcast/latest")
    public Result<Map<String, Object>> latestBroadcast() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT level,title,message,area,exit_name AS `exit`,DATE_FORMAT(publish_time,'%H:%i 更新') AS time FROM forest_broadcast ORDER BY publish_time DESC LIMIT 1");
        return Result.ok(rows.isEmpty() ? null : rows.get(0));
    }

    @GetMapping("/broadcast/history")
    public Result<List<Map<String, Object>>> broadcastHistory(@RequestParam(defaultValue = "8") Integer limit) {
        int size = limit == null ? 8 : Math.max(1, Math.min(limit, 30));
        return Result.ok(jdbc.queryForList(
                "SELECT id,level,title,message,area,exit_name AS `exit`," +
                        "DATE_FORMAT(publish_time,'%m-%d %H:%i') AS time" +
                        " FROM forest_broadcast ORDER BY publish_time DESC,id DESC LIMIT " + size));
    }

    @GetMapping("/equipment/bootstrap")
    public Result<Map<String, Object>> equipmentBootstrap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("devices", jdbc.queryForList("SELECT e.id,e.name,e.type,e.location,e.source,DATE_FORMAT(e.last_maintain,'%Y-%m-%d') AS lastMaintain,e.status,COALESCE(s.latitude,d.latitude) AS latitude,COALESCE(s.longitude,d.longitude) AS longitude FROM forest_equipment e LEFT JOIN forest_sensor_node s ON e.type='传感节点' AND s.id=e.id LEFT JOIN forest_drone d ON e.type='无人机' AND d.id=e.id ORDER BY e.id"));
        data.put("faults", jdbc.queryForList("SELECT id,device_name AS device,device_id AS deviceId,location,fault_type AS type,DATE_FORMAT(fault_time,'%Y-%m-%d %H:%i') AS time,status FROM forest_equipment_fault ORDER BY fault_time DESC"));
        data.put("history", jdbc.queryForList("SELECT DATE_FORMAT(record_time,'%Y-%m-%d %H:%i') AS time,device_name AS device,device_type AS type,fault,result,operator_name AS operator FROM forest_maintenance_record ORDER BY record_time DESC"));
        data.put("selfTests", equipmentSelfTestService.latest());
        return Result.ok(data);
    }

    @PostMapping("/equipment")
    @Transactional
    public Result<String> addEquipment(@RequestBody Map<String, Object> b) {
        String id = text(b.get("id"));
        String name = text(b.get("name"));
        String type = text(b.get("type"));
        String location = text(b.get("location"));
        String source = text(b.get("source"));
        boolean mapDevice = "传感节点".equals(type) || "无人机".equals(type);
        Double longitude = nullableDecimal(b.get("longitude"));
        Double latitude = nullableDecimal(b.get("latitude"));
        if (mapDevice && (longitude == null || latitude == null || !isInsidePark(longitude, latitude))) {
            return Result.fail("传感节点和无人机的坐标必须位于公园边界内");
        }
        jdbc.update("INSERT INTO forest_equipment(id,name,type,location,source,status) VALUES(?,?,?,?,?,'online')", id, name, type, location, source);
        if ("传感节点".equals(type)) {
            boolean real = "真机".equals(source);
            double mapX = (longitude - 119.260973D) / (119.301049D - 119.260973D) * 100D;
            double mapY = (26.181860D - latitude) / (26.181860D - 26.141567D) * 100D;
            jdbc.update("INSERT INTO forest_sensor_node(id,name,zone,smoke,temperature,humidity,co,online,source,status,camera,map_x,map_y,latitude,longitude) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    id, name, text(b.get("zone")), real ? null : 12D, real ? null : 29D, real ? null : 50D, real ? null : 3D,
                    real ? 0 : 1, real ? "real" : "simulated", real ? "offline" : "normal", null, mapX, mapY, latitude, longitude);
        } else if ("无人机".equals(type)) {
            double mapX = (longitude - 119.260973D) / (119.301049D - 119.260973D) * 100D;
            double mapY = (26.181860D - latitude) / (26.181860D - 26.141567D) * 100D;
            jdbc.update("INSERT INTO forest_drone(id,name,model,battery,status,location,thermal,operator_name,payloads,map_x,map_y,latitude,longitude,altitude,speed,satellites,link_quality,eta_sec,phase) VALUES(?,?,?,100,'idle',?,1,NULL,'可见光,热成像',?,?,?,?,0,0,0,0,0,'地面待命')",
                    id, name, name, location, mapX, mapY, latitude, longitude);
        }
        return Result.ok("设备已登记");
    }

    @PutMapping("/equipment/{id}/status")
    public Result<String> updateEquipment(@PathVariable String id, @RequestBody Map<String, Object> b) {
        jdbc.update("UPDATE forest_equipment SET status=?,last_maintain=IF(?='online',CURDATE(),last_maintain) WHERE id=?", text(b.get("status")), text(b.get("status")), id);
        return Result.ok("设备状态已保存");
    }

    @PutMapping("/equipment/faults/{id}")
    public Result<String> updateFault(@PathVariable String id, @RequestBody Map<String, Object> b) {
        jdbc.update("UPDATE forest_equipment_fault SET status=? WHERE id=?", text(b.get("status")), id);
        return Result.ok("异常状态已保存");
    }

    @PostMapping("/equipment/maintenance")
    @Transactional
    public Result<String> addMaintenance(@RequestBody Map<String, Object> b) {
        jdbc.update("INSERT INTO forest_maintenance_record(record_time,device_id,device_name,device_type,fault,result,operator_name) VALUES(NOW(),?,?,?,?,?,?)", text(b.get("deviceId")), text(b.get("device")), text(b.get("type")), text(b.get("fault")), text(b.get("result")), text(b.get("operator")));
        jdbc.update("UPDATE forest_equipment SET status='online',last_maintain=CURDATE() WHERE id=?", text(b.get("deviceId")));
        if (!text(b.get("faultId")).isEmpty()) jdbc.update("UPDATE forest_equipment_fault SET status='closed' WHERE id=?", text(b.get("faultId")));
        return Result.ok("维护记录已保存");
    }

    /**
     * 为课程演示生成一条平滑、可解释的返航遥测曲线，并与真机遥测使用同一张表。
     * 后续接入无人机 SDK 时只需继续调用 /telemetry，报告页无需改动。
     */
    private PatrolSimulation simulatePatrolTelemetry(String missionId, String droneId, Double requestedLongitude, Double requestedLatitude) {
        double startLongitude = requestedLongitude == null ? 119.281011D : requestedLongitude;
        double startLatitude = requestedLatitude == null ? 26.161714D : requestedLatitude;
        if (!droneId.isEmpty()) {
            List<Map<String, Object>> drones = jdbc.queryForList("SELECT longitude,latitude FROM forest_drone WHERE id=?", droneId);
            if (!drones.isEmpty()) {
                Map<String, Object> drone = drones.get(0);
                if (requestedLongitude == null && drone.get("longitude") instanceof Number) startLongitude = ((Number) drone.get("longitude")).doubleValue();
                if (requestedLatitude == null && drone.get("latitude") instanceof Number) startLatitude = ((Number) drone.get("latitude")).doubleValue();
            }
        }

        Random random = new Random(System.nanoTime() ^ missionId.hashCode());
        LocalDateTime now = LocalDateTime.now();
        int count = 12;
        double sumPm25 = 0D, sumCo = 0D, maxPm25 = 0D, maxCo = 0D, maxTemperature = 0D, maxSurface = 0D;
        double lastLatitude = startLatitude, lastLongitude = startLongitude;

        for (int i = 0; i < count; i++) {
            double progress = i / (double) (count - 1);
            double arc = Math.sin(progress * Math.PI);
            double latitude = startLatitude + Math.sin(progress * Math.PI * 2D) * 0.00115D + progress * 0.00032D;
            double longitude = startLongitude + arc * 0.00145D - progress * 0.00024D;
            double pm25 = round1(18D + 11D * arc + Math.sin(i * 1.2D) * 2.2D + random.nextDouble() * 1.8D);
            double co = round1(2.8D + 2D * arc + Math.sin(i * 0.8D) * 0.5D + random.nextDouble() * 0.5D);
            double temperature = round1(28.6D + 2.4D * arc + random.nextDouble() * 0.5D);
            double humidity = round1(57D - 9D * arc + random.nextDouble() * 1.4D);
            double surface = round1(31.5D + 9.5D * arc + Math.sin(i * 0.6D) * 1.2D + random.nextDouble());
            double wind = round1(2.1D + Math.sin(i * 0.7D) * 0.7D + random.nextDouble() * 0.4D);
            double altitude = round1(8D + 47D * arc);
            double speed = round1(i == 0 || i == count - 1 ? 1.2D : 4.2D + random.nextDouble() * 2.3D);
            double battery = round1(92D - i * 1.25D);
            int satellites = 16 + random.nextInt(4);
            int linkQuality = 96 - random.nextInt(7);
            LocalDateTime collectTime = now.minusMinutes(count - 1L - i);

            jdbc.update("INSERT INTO drone_telemetry(mission_id,drone_id,collect_time,latitude,longitude,altitude,speed,battery,satellites,link_quality,pm25,temperature,humidity,co,surface_temperature,wind_estimate) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    missionId, droneId, collectTime, latitude, longitude, altitude, speed, battery, satellites, linkQuality, pm25, temperature, humidity, co, surface, wind);
            sumPm25 += pm25;
            sumCo += co;
            maxPm25 = Math.max(maxPm25, pm25);
            maxCo = Math.max(maxCo, co);
            maxTemperature = Math.max(maxTemperature, temperature);
            maxSurface = Math.max(maxSurface, surface);
            lastLatitude = latitude;
            lastLongitude = longitude;
        }
        return new PatrolSimulation(count, round1(sumPm25 / count), round1(sumCo / count), round1(maxPm25), round1(maxCo), round1(maxTemperature), round1(maxSurface), lastLatitude, lastLongitude);
    }

    private PatrolSimulation analyzePersistedTelemetry(String missionId) {
        Map<String, Object> aggregate = jdbc.queryForMap("SELECT COUNT(*) AS sample_count,AVG(pm25) AS avg_pm25,AVG(co) AS avg_co,MAX(pm25) AS max_pm25,MAX(co) AS max_co,MAX(temperature) AS max_temperature,MAX(surface_temperature) AS max_surface FROM drone_telemetry WHERE mission_id=?", missionId);
        Map<String, Object> last = jdbc.queryForMap("SELECT latitude,longitude FROM drone_telemetry WHERE mission_id=? ORDER BY collect_time DESC,id DESC LIMIT 1", missionId);
        return new PatrolSimulation(
                number(aggregate.get("sample_count"), 0D).intValue(),
                round1(number(aggregate.get("avg_pm25"), 0D).doubleValue()),
                round1(number(aggregate.get("avg_co"), 0D).doubleValue()),
                round1(number(aggregate.get("max_pm25"), 0D).doubleValue()),
                round1(number(aggregate.get("max_co"), 0D).doubleValue()),
                round1(number(aggregate.get("max_temperature"), 0D).doubleValue()),
                round1(number(aggregate.get("max_surface"), 0D).doubleValue()),
                number(last.get("latitude"), 26.161714D).doubleValue(),
                number(last.get("longitude"), 119.281011D).doubleValue());
    }

    private String buildRuleSummary(String coverage, PatrolSimulation s, int hotspots) {
        String conclusion = hotspots > 0
                ? "发现地表温度异常热点，需要护林员携带热成像设备复查并检查周边可燃物。"
                : "PM2.5、CO与地表温度未出现同步越限，未形成明确火情证据。";
        return "本次仿真巡护覆盖" + coverage + "，生成" + s.count + "组连续遥测。PM2.5峰值" + s.maxPm25
                + "μg/m³、CO峰值" + s.maxCo + "ppm、环境温度最高" + s.maxTemperature + "℃、地表温度最高"
                + s.maxSurfaceTemperature + "℃。" + conclusion + "建议保留影像与曲线作为复查依据，并按计划继续巡护。";
    }

    private double round1(double value) {
        return Math.round(value * 10D) / 10D;
    }

    private static class PatrolSimulation {
        private final int count;
        private final double avgPm25;
        private final double avgCo;
        private final double maxPm25;
        private final double maxCo;
        private final double maxTemperature;
        private final double maxSurfaceTemperature;
        private final double lastLatitude;
        private final double lastLongitude;

        private PatrolSimulation(int count, double avgPm25, double avgCo, double maxPm25, double maxCo,
                                 double maxTemperature, double maxSurfaceTemperature, double lastLatitude, double lastLongitude) {
            this.count = count;
            this.avgPm25 = avgPm25;
            this.avgCo = avgCo;
            this.maxPm25 = maxPm25;
            this.maxCo = maxCo;
            this.maxTemperature = maxTemperature;
            this.maxSurfaceTemperature = maxSurfaceTemperature;
            this.lastLatitude = lastLatitude;
            this.lastLongitude = lastLongitude;
        }
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private String nullableText(Object value) { if (value == null) return null; String s = String.valueOf(value); return s.isEmpty() ? null : s; }
    private int integer(Object value) { return value == null || text(value).isEmpty() ? 0 : (int) Math.round(Double.parseDouble(text(value))); }
    private double decimal(Object value) { return value == null || text(value).isEmpty() ? 0D : Double.parseDouble(text(value)); }
    private Double nullableDecimal(Object value) { return value == null || text(value).isEmpty() ? null : Double.parseDouble(text(value)); }
    private Number number(Object value, Double fallback) { return value instanceof Number ? (Number) value : fallback; }

    private boolean isInsidePark(double longitude, double latitude) {
        List<String> rows = jdbc.query("SELECT geojson FROM forest_map WHERE id=1 AND geojson IS NOT NULL", (rs, n) -> rs.getString(1));
        if (rows.isEmpty()) return false;
        JSONObject geometry = JSON.parseObject(rows.get(0));
        JSONArray ring = geometry.getJSONArray("coordinates").getJSONArray(0);
        boolean inside = false;
        for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
            JSONArray a = ring.getJSONArray(i), b = ring.getJSONArray(j);
            double xi = a.getDoubleValue(0), yi = a.getDoubleValue(1);
            double xj = b.getDoubleValue(0), yj = b.getDoubleValue(1);
            if ((yi > latitude) != (yj > latitude)
                    && longitude < (xj - xi) * (latitude - yi) / (yj - yi) + xi) inside = !inside;
        }
        return inside;
    }
}
