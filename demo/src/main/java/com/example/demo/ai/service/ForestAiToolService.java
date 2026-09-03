package com.example.demo.ai.service;

import com.example.demo.ai.support.AiAuthService.Principal;
import com.example.demo.service.ForestPatrolSummaryService;
import com.example.demo.service.ForestRiskAnalysisService;
import com.example.demo.service.ForestWeatherService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 受控业务工具：全部只读、白名单、带参数校验与范围限制。
 * 模型永远接触不到 SQL 与任意方法名；编排层只能按固定工具名调用本类。
 * 查询逻辑复用现有表结构与 ForestWeatherService / ForestRiskAnalysisService /
 * ForestPatrolSummaryService，不复制业务规则。
 */
@Service
public class ForestAiToolService {

    /** 工具白名单（同时也是编排层的枚举来源）。 */
    public static final List<String> TOOL_NAMES = Arrays.asList(
            "queryActiveIncidents", "queryIncidentDetail", "queryDeviceStatus", "queryDeviceStatistics",
            "querySensorHistory", "queryHighRiskDevices", "queryDroneMission", "queryWeatherSummary",
            "generatePatrolSummary", "generateIncidentReport");

    /** 业务编号：FIRE-20240101-120000 / DEMO-FIRE-1700000000 / PATROL-... / EMERGENCY-... / GT-01 / UAV-01 */
    private static final Pattern BUSINESS_ID =
            Pattern.compile("\\b((?:DEMO-|EMERGENCY-|SIM-|PATROL-|FIRE-)?[A-Z]{1,8}-[A-Z0-9\\-]{1,40})\\b");
    private static final Pattern HOURS = Pattern.compile("(?:最近|过去|近)?\\s*(\\d{1,3})\\s*小时");

    private final JdbcTemplate jdbc;
    private final ForestWeatherService weatherService;
    private final ForestRiskAnalysisService riskAnalysisService;
    private final ForestPatrolSummaryService patrolSummaryService;

    public ForestAiToolService(JdbcTemplate jdbc, ForestWeatherService weatherService,
                               ForestRiskAnalysisService riskAnalysisService,
                               ForestPatrolSummaryService patrolSummaryService) {
        this.jdbc = jdbc;
        this.weatherService = weatherService;
        this.riskAnalysisService = riskAnalysisService;
        this.patrolSummaryService = patrolSummaryService;
    }

    // ---------- 统一入口 ----------

    public static class ToolResult {
        public final String tool;
        public boolean ok = true;
        public String error;
        public String sourceType = "business";
        public String sourceName = "";
        public Object facts;

        ToolResult(String tool) { this.tool = tool; }

        Map<String, Object> asPromptBlock() {
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("tool", tool);
            if (!ok) {
                block.put("available", false);
                block.put("note", error);
                return block;
            }
            block.put("available", true);
            block.put("description", sourceName);
            block.put("data", facts);
            return block;
        }
    }

    /** 唯一被编排层调用的入口；工具名与参数都经过校验。 */
    public ToolResult invoke(String toolName, Map<String, Object> params, Principal principal) {
        ToolResult result = new ToolResult(toolName);
        if (principal == null) {
            result.ok = false;
            result.error = "未登录用户不能查询业务数据";
            return result;
        }
        if (!TOOL_NAMES.contains(toolName)) {
            result.ok = false;
            result.error = "工具不在白名单内";
            return result;
        }
        try {
            if ("queryActiveIncidents".equals(toolName)) activeIncidents(result);
            else if ("queryIncidentDetail".equals(toolName)) incidentDetail(result, params);
            else if ("queryDeviceStatus".equals(toolName)) deviceStatus(result, params);
            else if ("queryDeviceStatistics".equals(toolName)) deviceStatistics(result);
            else if ("querySensorHistory".equals(toolName)) sensorHistory(result, params);
            else if ("queryHighRiskDevices".equals(toolName)) highRiskDevices(result);
            else if ("queryDroneMission".equals(toolName)) droneMission(result, params);
            else if ("queryWeatherSummary".equals(toolName)) weatherSummary(result);
            else if ("generatePatrolSummary".equals(toolName)) patrolSummary(result, params);
            else incidentReport(result, params);
        } catch (IllegalArgumentException e) {
            result.ok = false;
            result.error = e.getMessage();
        } catch (Exception e) {
            result.ok = false;
            result.error = "业务查询暂时失败，请稍后重试或人工在页面查看";
        }
        return result;
    }

    // ---------- 意图与参数抽取 ----------

    /** 根据问题决定要调用的工具（可多个）。纯规则判断，不经模型。 */
    public static List<Map<String, Object>> planTools(String question) {
        List<Map<String, Object>> calls = new ArrayList<>();
        String text = question == null ? "" : question;
        List<String> ids = extractIds(text);
        boolean wantsToday = contains(text, "今天", "今日", "当天");
        boolean mentionsIncident = contains(text, "火情", "警情", "事件", "火点");
        boolean mentionsDevice = contains(text, "设备", "传感器", "节点", "烟感");
        boolean mentionsDrone = contains(text, "无人机", "巡护任务", "任务");
        boolean mentionsWeather = contains(text, "天气", "气温", "风力", "风速", "降雨", "湿度");

        if (contains(text, "事件报告", "分析报告", "警情分析", "火情分析") && !incidentIds(ids).isEmpty()) {
            calls.add(call("generateIncidentReport", "incidentId", firstIncidentId(ids)));
        } else if (mentionsIncident && (contains(text, "未处理", "未结案", "活跃", "当前", "现在", "有哪些", "多少", "列表") || wantsToday)) {
            calls.add(call("queryActiveIncidents", null, null));
        }
        if (!incidentIds(ids).isEmpty() && !contains(text, "事件报告", "分析报告", "警情分析", "火情分析")) {
            calls.add(call("queryIncidentDetail", "incidentId", firstIncidentId(ids)));
        }
        if (contains(text, "高风险", "异常设备", "告警设备", "风险设备", "风险较高")) {
            calls.add(call("queryHighRiskDevices", null, null));
        }
        if (contains(text, "统计", "在线率", "多少台", "离线", "在线", "设备总数") && mentionsDevice) {
            calls.add(call("queryDeviceStatistics", null, null));
        }
        if (contains(text, "状态", "情况") && mentionsDevice && !sensorIds(ids).isEmpty()) {
            calls.add(call("queryDeviceStatus", "deviceId", firstSensorLikeId(ids)));
        }
        if ((contains(text, "历史", "趋势", "数据", "记录") || HOURS.matcher(text).find())
                && !sensorIds(ids).isEmpty() && mentionsDevice) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("deviceId", firstSensorLikeId(ids));
            params.put("hours", extractHours(text));
            calls.add(call("querySensorHistory", params));
        }
        if (!missionIds(ids).isEmpty() || (mentionsDrone && contains(text, "任务", "巡护", "进展", "结果", "状态", "哪些", "飞行"))) {
            Map<String, Object> params = new LinkedHashMap<>();
            if (!missionIds(ids).isEmpty()) params.put("missionId", missionIds(ids).get(0));
            calls.add(call("queryDroneMission", params));
        }
        if (mentionsWeather || (contains(text, "风险", "火险") && !contains(text, "高风险设备", "设备"))) {
            calls.add(call("queryWeatherSummary", null, null));
        }
        if (contains(text, "巡护总结", "日报", "今日总结", "生成总结", "总结报告", "汇报")) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("scope", wantsToday ? "today" : "recent");
            calls.add(call("generatePatrolSummary", params));
        }
        return calls;
    }

    static List<String> extractIds(String text) {
        List<String> ids = new ArrayList<>();
        Matcher matcher = BUSINESS_ID.matcher(text.toUpperCase(java.util.Locale.ROOT));
        while (matcher.find()) ids.add(matcher.group(1));
        return ids;
    }

    private static List<String> incidentIds(List<String> ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) if (id.startsWith("FIRE-") || id.startsWith("DEMO-FIRE-")) out.add(id);
        return out;
    }

    private static List<String> missionIds(List<String> ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) if (id.startsWith("PATROL-") || id.startsWith("EMERGENCY-")) out.add(id);
        return out;
    }

    private static List<String> sensorIds(List<String> ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            if (!id.startsWith("FIRE-") && !id.startsWith("DEMO-FIRE-") && !id.startsWith("PATROL-") && !id.startsWith("EMERGENCY-")) {
                out.add(id);
            }
        }
        return out;
    }

    private static String firstIncidentId(List<String> ids) { return incidentIds(ids).get(0); }
    private static String firstSensorLikeId(List<String> ids) { return sensorIds(ids).get(0); }

    static int extractHours(String text) {
        Matcher matcher = HOURS.matcher(text);
        if (matcher.find()) return clampHours(Integer.parseInt(matcher.group(1)));
        return 24;
    }

    static int clampHours(Integer hours) {
        if (hours == null) return 24;
        return Math.max(1, Math.min(hours, 72));
    }

    static int clampLimit(Integer limit, int fallback) {
        if (limit == null) return fallback;
        return Math.max(1, Math.min(limit, 200));
    }

    static String requireId(Object value, String label) {
        String id = value == null ? "" : String.valueOf(value).trim();
        if (id.isEmpty() || id.length() > 64 || !id.matches("^[\\u4e00-\\u9fa5A-Za-z0-9_\\-.]+$") || id.contains("..")) {
            throw new IllegalArgumentException(label + "不合法");
        }
        return id;
    }

    private static boolean contains(String text, String... keys) {
        for (String key : keys) if (text.contains(key)) return true;
        return false;
    }

    private static Map<String, Object> call(String tool, String key, Object value) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (key != null) params.put(key, value);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("tool", tool);
        entry.put("params", params);
        return entry;
    }

    private static Map<String, Object> call(String tool, Map<String, Object> params) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("tool", tool);
        entry.put("params", params == null ? new LinkedHashMap<String, Object>() : params);
        return entry;
    }

    // ---------- 具体工具（全部只读） ----------

    private void activeIncidents(ToolResult result) {
        result.sourceType = "incident";
        result.sourceName = "当前未结案火情事件";
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,event_time AS eventTime,zone,level,source,status,smoke,temperature,confidence," +
                        "analysis_model AS analysisModel,reason,ranger" +
                        " FROM forest_incident WHERE status<>'closed' ORDER BY event_time DESC LIMIT 20");
        Integer todayCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM forest_incident WHERE event_time>=CURDATE()", Integer.class);
        Integer todayOpenCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM forest_incident WHERE event_time>=CURDATE() AND status<>'closed'", Integer.class);
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("openCount", rows.size());
        facts.put("todayCreated", todayCount);
        facts.put("todayOpen", todayOpenCount);
        facts.put("incidents", compactIncidents(rows));
        facts.put("queryTime", now());
        result.facts = facts;
    }

    private List<Map<String, Object>> compactIncidents(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("time", row.get("eventTime"));
            item.put("zone", row.get("zone"));
            item.put("level", row.get("level"));
            item.put("status", row.get("status"));
            item.put("source", row.get("source"));
            item.put("smoke", row.get("smoke"));
            item.put("temperature", row.get("temperature"));
            item.put("reason", row.get("reason"));
            item.put("ranger", row.get("ranger"));
            out.add(item);
        }
        return out;
    }

    private void incidentDetail(ToolResult result, Map<String, Object> params) {
        String id = requireId(params.get("incidentId"), "事件编号");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,event_time AS eventTime,zone,level,source,status,smoke,temperature,confidence," +
                        "analysis_model AS analysisModel,analysis_detail AS analysisDetail,reason,result,ranger" +
                        " FROM forest_incident WHERE id=?", id);
        if (rows.isEmpty()) {
            result.ok = false;
            result.error = "事件 " + id + " 不存在";
            return;
        }
        result.sourceType = "incident";
        result.sourceName = "火情事件 " + id;
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("incident", rows.get(0));
        facts.put("linkedMissions", jdbc.queryForList(
                "SELECT m.id,m.name,m.status,m.progress,m.ranger,m.drone,l.incident_id AS incidentId" +
                        " FROM drone_mission m JOIN drone_mission_incident l ON l.mission_id=m.id WHERE l.incident_id=?", id));
        facts.put("queryTime", now());
        result.facts = facts;
    }

    private void deviceStatus(ToolResult result, Map<String, Object> params) {
        String id = requireId(params.get("deviceId"), "设备编号");
        Map<String, Object> facts = new LinkedHashMap<>();
        List<Map<String, Object>> equipment = jdbc.queryForList(
                "SELECT id,name,type,location,source,status,DATE_FORMAT(last_maintain,'%Y-%m-%d') AS lastMaintain" +
                        " FROM forest_equipment WHERE id=?", id);
        List<Map<String, Object>> sensors = jdbc.queryForList(
                "SELECT id,name,zone,smoke,temperature,humidity,co,online,source,status," +
                        "DATE_FORMAT(updated_at,'%Y-%m-%d %H:%i:%s') AS updatedAt FROM forest_sensor_node WHERE id=?", id);
        List<Map<String, Object>> drones = jdbc.queryForList(
                "SELECT id,name,model,battery,status,location,phase,altitude,speed,updated_at AS updatedAt" +
                        " FROM forest_drone WHERE id=?", id);
        if (equipment.isEmpty() && sensors.isEmpty() && drones.isEmpty()) {
            result.ok = false;
            result.error = "设备 " + id + " 不存在";
            return;
        }
        if (!sensors.isEmpty()) {
            facts.put("sensor", sensors.get(0));
            facts.put("latestReadings", jdbc.queryForList(
                    "SELECT DATE_FORMAT(collect_time,'%Y-%m-%d %H:%i:%s') AS collectTime,smoke,temperature,humidity,co,source" +
                            " FROM forest_sensor_reading WHERE sensor_id=? ORDER BY collect_time DESC LIMIT 5", id));
        }
        if (!drones.isEmpty()) facts.put("drone", drones.get(0));
        if (!equipment.isEmpty()) facts.put("equipment", equipment.get(0));
        facts.put("openFaults", jdbc.queryForList(
                "SELECT fault_type AS faultType,DATE_FORMAT(fault_time,'%Y-%m-%d %H:%i') AS faultTime,status" +
                        " FROM forest_equipment_fault WHERE device_id=? AND status<>'closed' LIMIT 5", id));
        facts.put("queryTime", now());
        result.sourceType = "device";
        result.sourceName = "设备 " + id + " 实时状态";
        result.facts = facts;
    }

    private void deviceStatistics(ToolResult result) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("equipment", jdbc.queryForMap(
                "SELECT COUNT(*) AS total," +
                        " SUM(CASE WHEN status='online' THEN 1 ELSE 0 END) AS online," +
                        " SUM(CASE WHEN status='offline' THEN 1 ELSE 0 END) AS offline," +
                        " SUM(CASE WHEN status='warning' THEN 1 ELSE 0 END) AS warning" +
                        " FROM forest_equipment"));
        facts.put("sensorStatusBreakdown", jdbc.queryForList(
                "SELECT status, COUNT(*) AS count FROM forest_sensor_node GROUP BY status"));
        facts.put("sensors", jdbc.queryForMap(
                "SELECT COUNT(*) AS total, SUM(CASE WHEN online=1 THEN 1 ELSE 0 END) AS online," +
                        " SUM(CASE WHEN online=0 THEN 1 ELSE 0 END) AS offline," +
                        " SUM(CASE WHEN status='alarm' THEN 1 ELSE 0 END) AS alarm" +
                        " FROM forest_sensor_node"));
        facts.put("drones", jdbc.queryForMap(
                "SELECT COUNT(*) AS total, SUM(CASE WHEN status='idle' THEN 1 ELSE 0 END) AS idle," +
                        " SUM(CASE WHEN status='flying' THEN 1 ELSE 0 END) AS flying," +
                        " SUM(CASE WHEN battery<20 THEN 1 ELSE 0 END) AS lowBattery FROM forest_drone"));
        facts.put("openFaults", jdbc.queryForObject(
                "SELECT COUNT(*) FROM forest_equipment_fault WHERE status<>'closed'", Integer.class));
        facts.put("queryTime", now());
        result.sourceType = "device";
        result.sourceName = "设备在线/离线/异常统计";
        result.facts = facts;
    }

    private void sensorHistory(ToolResult result, Map<String, Object> params) {
        String id = requireId(params.get("deviceId"), "设备编号");
        int hours = clampHours(params.get("hours") == null ? null : toInt(params.get("hours")));
        int maxPoints = clampLimit(params.get("maxPoints") == null ? null : toInt(params.get("maxPoints")), 60);
        List<Map<String, Object>> nodes = jdbc.queryForList(
                "SELECT id,name,zone,smoke,temperature,humidity,co,online,status FROM forest_sensor_node WHERE id=?", id);
        if (nodes.isEmpty()) {
            result.ok = false;
            result.error = "传感器 " + id + " 不存在";
            return;
        }
        Map<String, Object> aggregate = jdbc.queryForMap(
                "SELECT COUNT(*) AS sampleCount,AVG(smoke) AS avgSmoke,MAX(smoke) AS maxSmoke,MIN(smoke) AS minSmoke," +
                        " AVG(temperature) AS avgTemperature,MAX(temperature) AS maxTemperature," +
                        " AVG(humidity) AS avgHumidity,MIN(humidity) AS minHumidity," +
                        " AVG(co) AS avgCo,MAX(co) AS maxCo,MAX(collect_time) AS lastCollectTime" +
                        " FROM forest_sensor_reading WHERE sensor_id=? AND collect_time>=DATE_SUB(NOW(),INTERVAL ? HOUR)", id, hours);
        List<Map<String, Object>> samples = jdbc.queryForList(
                "SELECT DATE_FORMAT(collect_time,'%Y-%m-%d %H:%i:%s') AS collectTime,smoke,temperature,humidity,co" +
                        " FROM forest_sensor_reading WHERE sensor_id=? AND collect_time>=DATE_SUB(NOW(),INTERVAL ? HOUR)" +
                        " ORDER BY collect_time DESC LIMIT " + maxPoints, id, hours);
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("node", nodes.get(0));
        facts.put("windowHours", hours);
        facts.put("aggregate", roundAggregate(aggregate));
        facts.put("recentSamplesDesc", samples);
        facts.put("note", "sampleCount=0 表示该窗口内没有真机或模拟采样记录，不代表设备读数全为 0");
        result.sourceType = "sensor";
        result.sourceName = "设备" + id + "传感器最近" + hours + "小时记录（聚合）";
        result.facts = facts;
    }

    private Map<String, Object> roundAggregate(Map<String, Object> aggregate) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : aggregate.entrySet()) {
            if (entry.getValue() instanceof Number && !(entry.getValue() instanceof Integer)) {
                out.put(entry.getKey(), Math.round(((Number) entry.getValue()).doubleValue() * 10D) / 10D);
            } else {
                out.put(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    private void highRiskDevices(ToolResult result) {
        List<Map<String, Object>> nodes = jdbc.queryForList(
                "SELECT id,name,zone,smoke,temperature,humidity,co,online,source,status," +
                        "DATE_FORMAT(updated_at,'%Y-%m-%d %H:%i:%s') AS updatedAt FROM forest_sensor_node");
        List<Map<String, Object>> risky = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            List<String> reasons = new ArrayList<>();
            double smoke = num(node.get("smoke"));
            double co = num(node.get("co"));
            double temperature = num(node.get("temperature"));
            double humidity = num(node.get("humidity"));
            boolean online = num(node.get("online")) == 1;
            if (!online) reasons.add("设备离线");
            String nodeStatus = String.valueOf(node.get("status"));
            if ("alarm".equals(nodeStatus)) reasons.add("节点状态为告警(alarm)");
            else if ("warning".equals(nodeStatus)) reasons.add("节点状态为预警(warning)");
            if (smoke >= 50) reasons.add("烟雾 " + smoke + " ppm ≥ 告警阈值 50");
            if (co >= 25) reasons.add("CO " + co + " ppm 明显偏高");
            if (temperature >= 40) reasons.add("温度 " + temperature + " ℃ 异常偏高");
            if (online && humidity > 0 && humidity <= 25) reasons.add("湿度 " + humidity + "%RH 过干");
            if (reasons.isEmpty()) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", node.get("id"));
            item.put("name", node.get("name"));
            item.put("zone", node.get("zone"));
            item.put("online", online);
            item.put("smoke", node.get("smoke"));
            item.put("temperature", node.get("temperature"));
            item.put("humidity", node.get("humidity"));
            item.put("co", node.get("co"));
            item.put("updatedAt", node.get("updatedAt"));
            item.put("reasons", reasons);
            risky.add(item);
        }
        List<Map<String, Object>> faultDevices = jdbc.queryForList(
                "SELECT device_id AS id,device_name AS name,location,fault_type AS faultType," +
                        "DATE_FORMAT(fault_time,'%Y-%m-%d %H:%i') AS faultTime FROM forest_equipment_fault" +
                        " WHERE status<>'closed' ORDER BY fault_time DESC LIMIT 10");
        for (Map<String, Object> fault : faultDevices) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", fault.get("id"));
            item.put("name", fault.get("name"));
            item.put("zone", fault.get("location"));
            item.put("reasons", Arrays.asList("未闭环设备故障：" + fault.get("faultType")));
            risky.add(item);
        }
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("highRiskCount", risky.size());
        facts.put("devices", risky.size() > 12 ? risky.subList(0, 12) : risky);
        facts.put("thresholdNote", "阈值来自系统规则模型：烟雾≥50ppm 告警、≥90ppm 极高（AiReviewService），CO≥25ppm、温度≥40℃、湿度≤25%RH 为高风险参考");
        facts.put("queryTime", now());
        result.sourceType = "device";
        result.sourceName = "高风险设备清单（实时）";
        result.facts = facts;
    }

    private void droneMission(ToolResult result, Map<String, Object> params) {
        Map<String, Object> facts = new LinkedHashMap<>();
        Object rawId = params.get("missionId");
        if (rawId != null && !String.valueOf(rawId).isEmpty()) {
            String id = requireId(rawId, "任务编号");
            List<Map<String, Object>> missions = jdbc.queryForList(
                    "SELECT m.id,m.name,m.route,m.ranger,m.drone,m.mode,m.status,m.progress,m.plan_time AS planTime," +
                            "m.coverage,m.images,m.hotspots,m.samples,m.max_temperature AS maxTemperature,m.report," +
                            "m.summary_source AS summarySource,l.incident_id AS incidentId," +
                            "m.create_time AS createTime,m.update_time AS updateTime" +
                            " FROM drone_mission m LEFT JOIN drone_mission_incident l ON l.mission_id=m.id WHERE m.id=?", id);
            if (missions.isEmpty()) {
                result.ok = false;
                result.error = "巡护任务 " + id + " 不存在";
                return;
            }
            Map<String, Object> mission = missions.get(0);
            if (mission.get("report") != null && String.valueOf(mission.get("report")).length() > 400) {
                mission.put("report", String.valueOf(mission.get("report")).substring(0, 400) + "…");
            }
            facts.put("mission", mission);
            facts.put("telemetryAggregate", jdbc.queryForMap(
                    "SELECT COUNT(*) AS sampleCount,MAX(surface_temperature) AS maxSurfaceTemperature," +
                            "MAX(pm25) AS maxPm25,MAX(co) AS maxCo,MAX(altitude) AS maxAltitude" +
                            " FROM drone_telemetry WHERE mission_id=?", id));
            facts.put("photoCount", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM drone_mission_photo WHERE mission_id=?", Integer.class, id));
        } else {
            facts.put("activeMissions", jdbc.queryForList(
                    "SELECT id,name,ranger,drone,status,progress,plan_time AS planTime FROM drone_mission" +
                            " WHERE status<>'已完成' ORDER BY create_time DESC LIMIT 10"));
            facts.put("completedToday", jdbc.queryForList(
                    "SELECT id,name,ranger,drone,coverage,hotspots,samples,max_temperature AS maxTemperature" +
                            " FROM drone_mission WHERE status='已完成' AND update_time>=CURDATE() ORDER BY update_time DESC LIMIT 10"));
        }
        facts.put("drones", jdbc.queryForList(
                "SELECT id,name,battery,status,phase,location,altitude,speed,updated_at AS updatedAt FROM forest_drone"));
        facts.put("queryTime", now());
        result.sourceType = "drone";
        result.sourceName = rawId == null ? "无人机任务与巡护情况" : "无人机巡护任务 " + rawId;
        result.facts = facts;
    }

    private void weatherSummary(ToolResult result) {
        List<Map<String, Object>> sensors = jdbc.queryForList(
                "SELECT id,zone,smoke,temperature,humidity,co,online,source,status FROM forest_sensor_node WHERE online=1");
        Map<String, Object> map = jdbc.queryForMap(
                "SELECT COALESCE(center_lat,26.161714) AS centerLat,COALESCE(center_lng,119.281011) AS centerLng FROM forest_map WHERE id=1");
        double lat = ((Number) map.get("centerLat")).doubleValue();
        double lng = ((Number) map.get("centerLng")).doubleValue();
        Map<String, Object> weather = weatherService.current(lat, lng, sensors);
        Map<String, Object> risk = riskAnalysisService.analyzeFireRisk(weather, sensors);
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("weather", weather);
        facts.put("fireRisk", risk);
        facts.put("zoneCount", jdbc.queryForObject("SELECT COUNT(*) FROM forest_zone", Integer.class));
        facts.put("queryTime", now());
        result.sourceType = "weather";
        result.sourceName = "园区天气与火险等级（实时融合计算）";
        result.facts = facts;
    }

    private void patrolSummary(ToolResult result, Map<String, Object> params) {
        String scope = params.get("scope") == null ? "recent" : String.valueOf(params.get("scope"));
        boolean todayOnly = "today".equals(scope);
        List<Map<String, Object>> missions = jdbc.queryForList(
                "SELECT id,name,route,ranger,drone,status,progress,coverage,images,hotspots,samples," +
                        "max_temperature AS maxTemperature," +
                        (todayOnly ? "CASE WHEN update_time>=CURDATE() THEN '今日' ELSE '更早' END" : "'今日'") + " AS dayFlag" +
                        " FROM drone_mission" + (todayOnly ? " WHERE update_time>=CURDATE()" : "") +
                        " ORDER BY update_time DESC LIMIT 15");
        Map<String, Object> totals = jdbc.queryForMap(
                "SELECT COUNT(*) AS missionCount,SUM(CASE WHEN status='已完成' THEN 1 ELSE 0 END) AS completed," +
                        " SUM(hotspots) AS totalHotspots,MAX(max_temperature) AS maxSurface,COALESCE(SUM(images),0) AS totalImages" +
                        " FROM drone_mission" + (todayOnly ? " WHERE update_time>=CURDATE()" : ""));
        Integer incidentsToday = jdbc.queryForObject(
                "SELECT COUNT(*) FROM forest_incident WHERE event_time>=CURDATE()", Integer.class);
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("scope", todayOnly ? "今日" : "最近任务");
        facts.put("totals", totals);
        facts.put("incidentsToday", incidentsToday);
        facts.put("missions", missions);
        facts.put("queryTime", now());
        String factsText = com.alibaba.fastjson2.JSON.toJSONString(facts);
        String fallback = "共" + num(totals.get("missionCount")) + "次巡护，完成" + num(totals.get("completed"))
                + "次，热点" + num(totals.get("totalHotspots")) + "处。数据查询时间 " + now()
                + "。（AI 总结不可用，此为规则汇总）";
        ForestPatrolSummaryService.SummaryResult summary = patrolSummaryService.summarize(factsText, fallback);
        facts.put("ruleSummaryDraft", summary.report);
        facts.put("ruleSummarySource", summary.source);
        result.sourceType = "report";
        result.sourceName = todayOnly ? "今日巡护数据汇总" : "近期巡护数据汇总";
        result.facts = facts;
    }

    private void incidentReport(ToolResult result, Map<String, Object> params) {
        String id = requireId(params.get("incidentId"), "事件编号");
        List<Map<String, Object>> incidents = jdbc.queryForList(
                "SELECT id,event_time AS eventTime,zone,level,source,status,smoke,temperature,confidence," +
                        "analysis_model AS analysisModel,analysis_detail AS analysisDetail,reason,result,ranger" +
                        " FROM forest_incident WHERE id=?", id);
        if (incidents.isEmpty()) {
            result.ok = false;
            result.error = "事件 " + id + " 不存在";
            return;
        }
        Map<String, Object> incident = incidents.get(0);
        String zone = String.valueOf(incident.get("zone"));
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("incident", incident);
        facts.put("zoneSensors", jdbc.queryForList(
                "SELECT id,name,smoke,temperature,humidity,co,online,status FROM forest_sensor_node WHERE zone=?", zone));
        facts.put("linkedMissions", jdbc.queryForList(
                "SELECT m.id,m.name,m.status,m.progress,m.hotspots,m.max_temperature AS maxTemperature,m.report" +
                        " FROM drone_mission m JOIN drone_mission_incident l ON l.mission_id=m.id WHERE l.incident_id=?", id));
        facts.put("relatedBroadcasts", jdbc.queryForList(
                "SELECT level,title,DATE_FORMAT(publish_time,'%Y-%m-%d %H:%i') AS publishTime FROM forest_broadcast" +
                        " WHERE publish_time>=DATE_SUB(NOW(),INTERVAL 7 DAY) ORDER BY publish_time DESC LIMIT 5"));
        facts.put("queryTime", now());
        facts.put("dataBoundary", "以上为系统内可查询到的关联数据；缺失项请在报告中说明数据不足");
        result.sourceType = "incident";
        result.sourceName = "火情事件 " + id + " 关联分析数据";
        result.facts = facts;
    }

    // ---------- helpers ----------

    private static double num(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0D;
    }

    private static Integer toInt(Object value) {
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String now() {
        return java.time.LocalDateTime.now().withNano(0).toString();
    }
}
