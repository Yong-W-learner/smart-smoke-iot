package com.example.demo.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 模拟设备固件主动自检，并将失败项转换为设备故障事件。 */
@Service
public class ForestEquipmentSelfTestService {

    private final JdbcTemplate jdbc;

    public ForestEquipmentSelfTestService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Map<String, Object> start(String deviceId) {
        List<String> running = jdbc.query("SELECT test_no FROM forest_equipment_self_test WHERE device_id=? AND status='running' ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> rs.getString(1), deviceId);
        if (!running.isEmpty()) return get(running.get(0));
        List<Map<String, Object>> devices = jdbc.queryForList(
                "SELECT id,name,type,location,source,status FROM forest_equipment WHERE id=?", deviceId);
        if (devices.isEmpty()) return null;
        Map<String, Object> device = devices.get(0);
        String testNo = "CHECK-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        jdbc.update("INSERT INTO forest_equipment_self_test(test_no,device_id,device_name,device_type,source,status,request_time) VALUES(?,?,?,?,?,'running',NOW())",
                testNo, deviceId, text(device.get("name")), text(device.get("type")), "simulated");
        return get(testNo);
    }

    public Map<String, Object> get(String testNo) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT test_no AS testNo,device_id AS deviceId,device_name AS deviceName,device_type AS deviceType,source,status,DATE_FORMAT(request_time,'%Y-%m-%d %H:%i:%s') AS requestTime,DATE_FORMAT(complete_time,'%Y-%m-%d %H:%i:%s') AS completeTime,result_json AS resultJson,summary FROM forest_equipment_self_test WHERE test_no=?",
                testNo);
        return rows.isEmpty() ? null : normalize(rows.get(0));
    }

    public List<Map<String, Object>> latest() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT test_no AS testNo,device_id AS deviceId,device_name AS deviceName,device_type AS deviceType,source,status,DATE_FORMAT(request_time,'%Y-%m-%d %H:%i:%s') AS requestTime,DATE_FORMAT(complete_time,'%Y-%m-%d %H:%i:%s') AS completeTime,result_json AS resultJson,summary FROM forest_equipment_self_test ORDER BY request_time DESC,id DESC LIMIT 30");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) result.add(normalize(row));
        return result;
    }

    @Scheduled(fixedDelay = 800)
    @Transactional
    public void completeRunningTests() {
        List<String> tests = jdbc.query(
                "SELECT test_no FROM forest_equipment_self_test WHERE status='running' AND request_time<=DATE_SUB(NOW(),INTERVAL 2 SECOND) ORDER BY id LIMIT 10",
                (rs, rowNum) -> rs.getString(1));
        for (String testNo : tests) complete(testNo);
    }

    /** 平台侧健康巡检：无需用户点击，自主把已发生的设备异常写入故障中心。 */
    @Scheduled(fixedDelay = 5000, initialDelay = 3000)
    @Transactional
    public void monitorEquipmentHealth() {
        List<Map<String, Object>> offlineSensors = jdbc.queryForList(
                "SELECT e.id,e.name,e.location,e.status FROM forest_equipment e JOIN forest_sensor_node s ON s.id=e.id WHERE e.status<>'retired' AND (s.online=0 OR s.status='offline')");
        for (Map<String, Object> device : offlineSensors) ensureFault(device, "通信离线", "offline");

        List<Map<String, Object>> offlineCameras = jdbc.queryForList(
                "SELECT e.id,e.name,e.location,e.status FROM forest_equipment e LEFT JOIN forest_sensor_node s ON s.camera=e.id WHERE e.type='固定摄像头' AND e.status<>'retired' GROUP BY e.id,e.name,e.location,e.status HAVING COALESCE(MAX(s.online),0)=0");
        for (Map<String, Object> device : offlineCameras) ensureFault(device, "视频链路中断", "offline");

        List<Map<String, Object>> abnormalDrones = jdbc.queryForList(
                "SELECT e.id,e.name,e.location,e.status,d.status AS runtime_status,d.battery FROM forest_equipment e JOIN forest_drone d ON d.id=e.id WHERE e.status<>'retired' AND (d.status='offline' OR d.battery<20)");
        for (Map<String, Object> device : abnormalDrones) {
            boolean offline = "offline".equals(device.get("runtime_status"));
            ensureFault(device, offline ? "飞控通信离线" : "电池电量低于20%", offline ? "offline" : "warning");
        }
    }

    private void complete(String testNo) {
        Map<String, Object> record = jdbc.queryForMap(
                "SELECT device_id,device_name,device_type FROM forest_equipment_self_test WHERE test_no=? FOR UPDATE", testNo);
        String deviceId = text(record.get("device_id"));
        Map<String, Object> device = jdbc.queryForMap(
                "SELECT id,name,type,location,source,status FROM forest_equipment WHERE id=?", deviceId);
        String type = text(device.get("type"));
        boolean offline = "offline".equals(device.get("status"));
        boolean warning = "warning".equals(device.get("status"));

        if ("传感节点".equals(type)) {
            List<Map<String, Object>> nodes = jdbc.queryForList("SELECT online,status FROM forest_sensor_node WHERE id=?", deviceId);
            if (!nodes.isEmpty()) offline = number(nodes.get(0).get("online")) == 0 || "offline".equals(nodes.get(0).get("status"));
        } else if ("固定摄像头".equals(type)) {
            Integer onlineCount = jdbc.queryForObject("SELECT COUNT(*) FROM forest_sensor_node WHERE camera=? AND online=1", Integer.class, deviceId);
            offline = onlineCount == null || onlineCount == 0;
        }

        JSONArray checks = checksFor(type, offline, warning);
        List<String> failedNames = new ArrayList<>();
        for (int i = 0; i < checks.size(); i++) {
            JSONObject check = checks.getJSONObject(i);
            if ("failed".equals(check.getString("status"))) failedNames.add(check.getString("name"));
        }
        boolean passed = failedNames.isEmpty();
        String summary = passed
                ? "设备完成全部自检项目，通信、数据与关键部件状态正常。"
                : "自检发现异常：" + String.join("、", failedNames) + "。系统已自动生成设备故障，等待护林员处置。";
        jdbc.update("UPDATE forest_equipment_self_test SET status=?,complete_time=NOW(),result_json=?,summary=? WHERE test_no=? AND status='running'",
                passed ? "passed" : "failed", JSON.toJSONString(checks), summary, testNo);

        if (!passed) createFaultIfMissing(device, failedNames, offline);
    }

    private JSONArray checksFor(String type, boolean offline, boolean warning) {
        JSONArray checks = new JSONArray();
        checks.add(check("通信链路", !offline, offline ? "设备无心跳响应" : "心跳响应 86 ms", "10秒内响应"));
        checks.add(check("数据上报", !offline, offline ? "未收到遥测数据" : "数据帧连续且字段完整", "连续3帧有效数据"));
        checks.add(check("供电状态", !offline, offline ? "无法读取供电状态" : "电压处于正常范围", "工作电压正常"));
        if ("传感节点".equals(type)) {
            checks.add(check("传感器采样回路", !offline, offline ? "ADC采样无响应" : "基线与响应值正常", "采样值非冻结且未越界"));
            checks.add(check("声光告警回执", !offline, offline ? "指令执行超时" : "蜂鸣器与LED执行成功", "命令下发并返回ACK"));
        } else if ("固定摄像头".equals(type)) {
            checks.add(check("视频流", !offline, offline ? "视频流中断" : "码流连续，帧率正常", "视频流可拉取"));
            checks.add(check("云台与存储", !offline, offline ? "控制指令无响应" : "云台回转与抓拍成功", "控制及抓拍返回成功"));
        } else if ("无人机".equals(type)) {
            checks.add(check("飞控与GNSS", !offline, offline ? "飞控失联" : "飞控在线，GNSS定位有效", "飞控在线且定位有效"));
            checks.add(check("电池健康度", !warning && !offline, warning ? "电池健康度低于维护阈值" : offline ? "无法读取电池信息" : "电池健康度正常", "健康度不低于80%"));
            checks.add(check("图传与任务载荷", !offline, offline ? "图传与载荷无响应" : "图传、热成像载荷在线", "图传和载荷均在线"));
        } else if ("广播设备".equals(type)) {
            checks.add(check("功放与扬声器", !offline, offline ? "功放回执超时" : "测试音播放回执正常", "测试音与功放状态正常"));
        }
        return checks;
    }

    private JSONObject check(String name, boolean passed, String value, String standard) {
        JSONObject item = new JSONObject();
        item.put("name", name);
        item.put("status", passed ? "passed" : "failed");
        item.put("value", value);
        item.put("standard", standard);
        return item;
    }

    private void createFaultIfMissing(Map<String, Object> device, List<String> failedNames, boolean offline) {
        ensureFault(device, String.join("、", failedNames) + "异常", offline ? "offline" : "warning");
    }

    private void ensureFault(Map<String, Object> device, String faultType, String equipmentStatus) {
        String deviceId = text(device.get("id"));
        Integer open = jdbc.queryForObject(
                "SELECT COUNT(*) FROM forest_equipment_fault WHERE device_id=? AND status IN ('pending','accepted')",
                Integer.class, deviceId);
        if (open == null || open == 0) {
            String faultId = "AUTO-" + System.currentTimeMillis() + "-" + String.format("%08x", deviceId.hashCode());
            jdbc.update("INSERT INTO forest_equipment_fault(id,device_id,device_name,location,fault_type,fault_time,status) VALUES(?,?,?,?,?,NOW(),'pending')",
                    faultId, deviceId, text(device.get("name")) + " " + deviceId, text(device.get("location")), faultType);
        }
        jdbc.update("UPDATE forest_equipment SET status=? WHERE id=?", equipmentStatus, deviceId);
    }

    private Map<String, Object> normalize(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>(row);
        String json = text(result.remove("resultJson"));
        result.put("checks", json.isEmpty() ? new JSONArray() : JSON.parseArray(json));
        return result;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int number(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }
}
