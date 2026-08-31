package com.example.demo.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 为既有库补充巡护业务新增字段（手动巡护、经纬度、分区边界、地图视图），并做坐标体系迁移：
 * 旧演示数据用虚拟坐标 119.73~119.74 / 30.32~30.33（指向皖南随机山地），统一迁到福州国家森林公园
 * 119.261~119.301 / 26.142~26.182。以 latitude>28 判定「仍是旧演示数据」（旧数据纬度都在 30 附近），福州真实坐标（约 26.16）不覆盖。
 */
@Component
public class ForestSchemaMigration implements CommandLineRunner {

    // 福州国家森林公园公开边界的 GCJ-02 外接范围
    private static final double WEST = 119.260973, EAST = 119.301049;
    private static final double SOUTH = 26.141567, NORTH = 26.181860;

    private final JdbcTemplate jdbcTemplate;

    public ForestSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        // drone_mission：当前业务仅保留护林员现场手动巡护
        addColumnIfMissing("drone_mission", "mode", "ALTER TABLE drone_mission ADD COLUMN mode VARCHAR(16) NOT NULL DEFAULT 'manual' AFTER drone");
        // drone_mission：返航时回写无人机与护林员位置
        addColumnIfMissing("drone_mission", "ranger_latitude", "ALTER TABLE drone_mission ADD COLUMN ranger_latitude DOUBLE NULL AFTER report");
        addColumnIfMissing("drone_mission", "ranger_longitude", "ALTER TABLE drone_mission ADD COLUMN ranger_longitude DOUBLE NULL AFTER ranger_latitude");
        addColumnIfMissing("drone_mission", "drone_latitude", "ALTER TABLE drone_mission ADD COLUMN drone_latitude DOUBLE NULL AFTER ranger_longitude");
        addColumnIfMissing("drone_mission", "drone_longitude", "ALTER TABLE drone_mission ADD COLUMN drone_longitude DOUBLE NULL AFTER drone_latitude");
        addColumnIfMissing("drone_mission", "summary_source", "ALTER TABLE drone_mission ADD COLUMN summary_source VARCHAR(20) NOT NULL DEFAULT 'rules' AFTER report");
        // 区域/传感器：补经纬度
        addColumnIfMissing("forest_zone", "latitude", "ALTER TABLE forest_zone ADD COLUMN latitude DOUBLE NULL AFTER trees");
        addColumnIfMissing("forest_zone", "longitude", "ALTER TABLE forest_zone ADD COLUMN longitude DOUBLE NULL AFTER latitude");
        addColumnIfMissing("forest_zone", "geojson", "ALTER TABLE forest_zone ADD COLUMN geojson LONGTEXT NULL AFTER longitude");
        addColumnIfMissing("forest_sensor_node", "latitude", "ALTER TABLE forest_sensor_node ADD COLUMN latitude DOUBLE NULL AFTER map_y");
        addColumnIfMissing("forest_sensor_node", "longitude", "ALTER TABLE forest_sensor_node ADD COLUMN longitude DOUBLE NULL AFTER latitude");
        // 地图视图状态（中心 + 缩放）
        addColumnIfMissing("forest_map", "center_lng", "ALTER TABLE forest_map ADD COLUMN center_lng DOUBLE NULL AFTER north");
        addColumnIfMissing("forest_map", "center_lat", "ALTER TABLE forest_map ADD COLUMN center_lat DOUBLE NULL AFTER center_lng");
        addColumnIfMissing("forest_map", "zoom", "ALTER TABLE forest_map ADD COLUMN zoom INT NULL AFTER center_lat");
        addColumnIfMissing("forest_incident", "analysis_model", "ALTER TABLE forest_incident ADD COLUMN analysis_model VARCHAR(80) NULL AFTER confidence");
        addColumnIfMissing("forest_incident", "analysis_detail", "ALTER TABLE forest_incident ADD COLUMN analysis_detail VARCHAR(1000) NULL AFTER analysis_model");

        migrateToManualPatrolOnly();

        // 坐标体系迁移：旧演示经纬度（latitude>28）重投影到福州
        seedZoneCoordinates();
        seedSensorCoordinates();
        seedDroneCoordinates();
        seedMapBounds();
        seedRangerCoordinates();
        relocateMapPointsInsidePark();
        synchronizeEquipmentLedger();
    }

    private void addColumnIfMissing(String table, String column, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?",
                Integer.class, table, column);
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void migrateToManualPatrolOnly() {
        jdbcTemplate.execute("ALTER TABLE drone_mission MODIFY COLUMN mode VARCHAR(16) NOT NULL DEFAULT 'manual'");
        jdbcTemplate.update("UPDATE drone_mission SET status='待执行', progress=0, eta_sec=0 WHERE status IN ('执行中','返航中')");
        jdbcTemplate.update("UPDATE drone_mission SET mode='manual' WHERE mode IS NULL OR mode<>'manual'");
        jdbcTemplate.update("UPDATE drone_mission SET route=REPLACE(route,'自动返航','返回起降点') WHERE route LIKE '%自动返航%'");
        jdbcTemplate.update("UPDATE forest_drone d SET d.status='idle',d.phase='地面待命',d.eta_sec=0,d.altitude=0,d.speed=0 WHERE d.status='flying' AND NOT EXISTS (SELECT 1 FROM drone_mission m WHERE m.drone=d.name AND m.status='手动飞行')");
    }

    private void seedZoneCoordinates() {
        jdbcTemplate.update("UPDATE forest_zone SET latitude = " + NORTH + " - map_y/100*" + (NORTH - SOUTH) + ", longitude = " + WEST + " + map_x/100*" + (EAST - WEST) + " WHERE latitude IS NULL OR latitude > 28");
    }

    private void seedSensorCoordinates() {
        jdbcTemplate.update("UPDATE forest_sensor_node SET latitude = " + NORTH + " - map_y/100*" + (NORTH - SOUTH) + ", longitude = " + WEST + " + map_x/100*" + (EAST - WEST) + " WHERE latitude IS NULL OR latitude > 28");
    }

    private void seedDroneCoordinates() {
        jdbcTemplate.update("UPDATE forest_drone SET latitude = " + NORTH + " - map_y/100*" + (NORTH - SOUTH) + ", longitude = " + WEST + " + map_x/100*" + (EAST - WEST) + " WHERE latitude IS NULL OR latitude > 28");
    }

    private void seedMapBounds() {
        jdbcTemplate.update("UPDATE forest_map SET west=" + WEST + ", south=" + SOUTH + ", east=" + EAST + ", north=" + NORTH + " WHERE id=1 AND (west IS NULL OR north > 28)");
        jdbcTemplate.update("UPDATE forest_map SET center_lng=119.281011, center_lat=26.161714, zoom=14 WHERE id=1 AND (center_lng IS NULL OR center_lat > 28)");
    }

    private void seedRangerCoordinates() {
        jdbcTemplate.update("UPDATE ranger_position SET latitude=26.16158, longitude=119.28625 WHERE ranger='林海' AND latitude > 28");
        jdbcTemplate.update("UPDATE ranger_position SET latitude=26.16400, longitude=119.29000 WHERE ranger='周岚' AND latitude > 28");
    }

    private void relocateMapPointsInsidePark() {
        List<String> maps = jdbcTemplate.query("SELECT geojson FROM forest_map WHERE id=1 AND geojson IS NOT NULL", (rs, n) -> rs.getString(1));
        if (maps.isEmpty()) return;
        JSONObject geometry = JSON.parseObject(maps.get(0));
        JSONArray ring = geometry.getJSONArray("coordinates").getJSONArray(0);
        relocateTable("forest_sensor_node", ring);
        relocateTable("forest_drone", ring);
    }

    /**
     * 设备台账是运维入口，监测节点、无人机和节点关联摄像头都必须有对应档案。
     * 只补录缺失设备，避免覆盖护林员已经维护过的状态和最近检查时间。
     */
    private void synchronizeEquipmentLedger() {
        jdbcTemplate.update(
                "INSERT IGNORE INTO forest_equipment(id,name,type,location,source,last_maintain,status) " +
                "SELECT s.id,s.name,'传感节点',s.zone," +
                "CASE WHEN s.source='real' THEN '真机' ELSE '仿真' END,NULL," +
                "CASE WHEN s.online=0 OR s.status='offline' THEN 'offline' WHEN s.status IN ('alarm','warning') THEN 'warning' ELSE 'online' END " +
                "FROM forest_sensor_node s");

        jdbcTemplate.update(
                "INSERT IGNORE INTO forest_equipment(id,name,type,location,source,last_maintain,status) " +
                "SELECT d.id,d.name,'无人机',d.location,'仿真',NULL," +
                "CASE WHEN d.status='offline' THEN 'offline' ELSE 'online' END " +
                "FROM forest_drone d");

        jdbcTemplate.update(
                "INSERT IGNORE INTO forest_equipment(id,name,type,location,source,last_maintain,status) " +
                "SELECT s.camera," +
                "MAX(CASE s.camera WHEN 'CAM-01' THEN '古树区一号球机' WHEN 'CAM-02' THEN '香樟区二号球机' WHEN 'CAM-03' THEN '露营区三号球机' ELSE CONCAT(s.zone,'固定摄像头') END)," +
                "'固定摄像头',GROUP_CONCAT(DISTINCT s.zone ORDER BY s.zone SEPARATOR '、')," +
                "CASE WHEN s.camera='CAM-01' THEN '真机' ELSE '仿真' END,NULL," +
                "CASE WHEN MAX(s.online)=1 THEN 'online' ELSE 'offline' END " +
                "FROM forest_sensor_node s WHERE s.camera IS NOT NULL AND s.camera<>'' GROUP BY s.camera");
    }

    private void relocateTable(String table, JSONArray ring) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id,longitude,latitude FROM " + table + " WHERE longitude IS NOT NULL AND latitude IS NOT NULL");
        for (Map<String, Object> row : rows) {
            double lng = ((Number) row.get("longitude")).doubleValue();
            double lat = ((Number) row.get("latitude")).doubleValue();
            if (inside(ring, lng, lat)) continue;
            double[] fixed = moveTowardParkCenter(ring, lng, lat);
            jdbcTemplate.update("UPDATE " + table + " SET longitude=?,latitude=? WHERE id=?", fixed[0], fixed[1], row.get("id"));
        }
    }

    private double[] moveTowardParkCenter(JSONArray ring, double lng, double lat) {
        double centerLng = 119.281011D, centerLat = 26.161714D;
        for (int step = 1; step <= 20; step++) {
            double ratio = step / 20D;
            double candidateLng = lng + (centerLng - lng) * ratio;
            double candidateLat = lat + (centerLat - lat) * ratio;
            if (inside(ring, candidateLng, candidateLat)) return new double[]{candidateLng, candidateLat};
        }
        return new double[]{centerLng, centerLat};
    }

    private boolean inside(JSONArray ring, double longitude, double latitude) {
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
