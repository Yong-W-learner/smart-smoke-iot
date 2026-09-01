package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 森林公园火险监测场景 数据库升级（幂等）。
 *
 * 在保留宿舍历史数据（building/floor/room 作为兼容字段，
 * 绝不删除）的前提下，为森林火险业务补充：
 *
 * 1. device 表追加森林监测节点字段：
 *    zone_id / node_code / node_name / latitude / longitude
 *    / map_x / map_y，并为 node_code 建立唯一索引；
 *
 * 2. alarm 表追加森林火险事件字段：
 *    scene_type / zone_id / fire_confidence_score
 *    / fire_weather_score / ancient_tree_impact_score
 *    / wildlife_impact_score / priority_score
 *    / priority_level / priority_reason / drone_confirmed；
 *    历史告警 scene_type 统一回填 DORM_LEGACY；
 *
 * 3. sys_user 表追加 zone_id（巡护区域授权）；
 *
 * 4. 创建并播种森林生态资源表：
 *    forest_zone（Z01~Z04）、ancient_tree（AT-001~AT-005）、
 *    ancient_tree_inspection、wildlife_habitat（WH-001~WH-004）、
 *    environment_record（DEMO 气象）、drone_mission、ecological_followup；
 *
 * 5. 播种森林监测节点：设备 1001 保持 REAL（FS-N-001），
 *    1002~1090 为 DEMO 监测节点（FS-N-002~FS-N-090），
 *    全部映射到 4 个森林分区并赋予地图坐标。
 *
 * 每次启动自动执行，列/表/数据已存在时跳过，可重复运行。
 */
@Component
public class ForestDatabaseInitializer {

    private static final Logger log =
            LoggerFactory.getLogger(ForestDatabaseInitializer.class);

    /** 森林监测节点设备ID范围（与历史宿舍设备ID保持一致） */
    static final long MIN_DEVICE_ID = 1001L;
    static final long MAX_DEVICE_ID = 1090L;

    /** 真实硬件监测节点 */
    static final long REAL_NODE_DEVICE_ID = 1001L;

    /**
     * 每个分区的地图中心坐标（SVG 1000×650）与地理坐标基准：
     * {mapX, mapY, 纬度, 经度}
     */
    private static final double[][] ZONE_BASE = {
            {300, 180, 28.180, 113.100}, // Z01 北部核心保护区
            {560, 180, 28.180, 113.140}, // Z02 东部防火通道区
            {300, 420, 28.150, 113.100}, // Z03 西部生态保育区
            {560, 420, 28.150, 113.140}  // Z04 南部生态涵养区
    };

    private final JdbcTemplate jdbcTemplate;

    public ForestDatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {

        boolean deviceTableReady = tableExists("device");

        if (deviceTableReady) {
            addDeviceForestColumns();
            addAlarmForestColumns();
            addUserZoneColumn();
        } else {
            log.warn(
                    "device 表不存在，跳过森林监测节点相关列扩展"
                            + "（森林生态资源表仍会创建）"
            );
        }

        createForestTables();
        seedZones();
        seedTrees();
        seedInspections();
        seedHabitats();
        seedEnvironmentRecords();
        seedPatrolUser();
        migratePatrolRole();

        if (deviceTableReady) {
            seedForestNodes();
            ensureUniqueIndex(
                    "device",
                    "uk_device_node_code",
                    "node_code"
            );
        }

        if (tableExists("alarm")) {
            backfillLegacyAlarmSceneType();
        }

        log.info(
                "森林公园火险监测数据库初始化完成："
                        + "4 个森林分区 / 5 株古树 / 4 个栖息地 / "
                        + "90 个监测节点（1001=REAL，1002~1090=DEMO），"
                        + "历史告警已标记 DORM_LEGACY，全部幂等"
        );
    }


    /* ==================================================
       表结构扩展
       ================================================== */

    private void addDeviceForestColumns() {

        addColumnIfMissing("device", "zone_id", "BIGINT NULL");
        addColumnIfMissing("device", "node_code", "VARCHAR(32) NULL");
        addColumnIfMissing("device", "node_name", "VARCHAR(64) NULL");
        addColumnIfMissing("device", "latitude", "DECIMAL(10,7) NULL");
        addColumnIfMissing("device", "longitude", "DECIMAL(10,7) NULL");
        addColumnIfMissing("device", "map_x", "DOUBLE NULL");
        addColumnIfMissing("device", "map_y", "DOUBLE NULL");
    }

    private void addAlarmForestColumns() {

        addColumnIfMissing("alarm", "scene_type", "VARCHAR(16) NULL");
        addColumnIfMissing("alarm", "zone_id", "BIGINT NULL");
        addColumnIfMissing("alarm", "fire_confidence_score", "INT NULL");
        addColumnIfMissing("alarm", "fire_weather_score", "INT NULL");
        addColumnIfMissing("alarm", "ancient_tree_impact_score", "INT NULL");
        addColumnIfMissing("alarm", "wildlife_impact_score", "INT NULL");
        addColumnIfMissing("alarm", "priority_score", "INT NULL");
        addColumnIfMissing("alarm", "priority_level", "VARCHAR(16) NULL");
        addColumnIfMissing("alarm", "priority_reason", "VARCHAR(255) NULL");
        addColumnIfMissing(
                "alarm",
                "drone_confirmed",
                "TINYINT NOT NULL DEFAULT 0"
        );
    }

    private void addUserZoneColumn() {

        if (tableExists("sys_user")) {
            addColumnIfMissing("sys_user", "zone_id", "BIGINT NULL");
        }
    }


    /* ==================================================
       森林生态资源表
       ================================================== */

    private void createForestTables() {

        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS forest_zone ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "zone_code VARCHAR(16) NOT NULL,"
                        + "zone_name VARCHAR(64) NOT NULL,"
                        + "description VARCHAR(255) NULL,"
                        + "area_km2 DECIMAL(8,2) NULL,"
                        + "map_center_x DOUBLE NULL,"
                        + "map_center_y DOUBLE NULL,"
                        + "risk_level VARCHAR(16) NULL,"
                        + "created_at DATETIME NOT NULL,"
                        + "UNIQUE KEY uk_fz_zone_code (zone_code)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );

        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS ancient_tree ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "tree_code VARCHAR(32) NOT NULL,"
                        + "tree_name VARCHAR(64) NOT NULL,"
                        + "scientific_name VARCHAR(64) NULL,"
                        + "species VARCHAR(64) NULL,"
                        + "age_years INT NULL,"
                        + "protection_level VARCHAR(16) NULL,"
                        + "zone_id BIGINT NULL,"
                        + "zone_name VARCHAR(64) NULL,"
                        + "node_code VARCHAR(32) NULL,"
                        + "latitude DECIMAL(10,7) NULL,"
                        + "longitude DECIMAL(10,7) NULL,"
                        + "map_x DOUBLE NULL,"
                        + "map_y DOUBLE NULL,"
                        + "diameter_cm DECIMAL(8,2) NULL,"
                        + "height_m DECIMAL(8,2) NULL,"
                        + "health_state VARCHAR(16) NULL,"
                        + "description VARCHAR(255) NULL,"
                        + "created_at DATETIME NOT NULL,"
                        + "UNIQUE KEY uk_at_tree_code (tree_code),"
                        + "KEY idx_at_zone_id (zone_id)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );

        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS ancient_tree_inspection ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "tree_id BIGINT NOT NULL,"
                        + "tree_code VARCHAR(32) NOT NULL,"
                        + "inspection_date DATETIME NULL,"
                        + "inspector VARCHAR(64) NULL,"
                        + "health_state VARCHAR(16) NULL,"
                        + "pest_risk VARCHAR(16) NULL,"
                        + "description VARCHAR(255) NULL,"
                        + "created_at DATETIME NOT NULL,"
                        + "KEY idx_ati_tree_id (tree_id)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );

        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS wildlife_habitat ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "habitat_code VARCHAR(32) NOT NULL,"
                        + "habitat_name VARCHAR(64) NOT NULL,"
                        + "zone_id BIGINT NULL,"
                        + "zone_name VARCHAR(64) NULL,"
                        + "protection_level VARCHAR(16) NULL,"
                        + "species_keywords VARCHAR(128) NULL,"
                        + "latitude DECIMAL(10,7) NULL,"
                        + "longitude DECIMAL(10,7) NULL,"
                        + "map_x DOUBLE NULL,"
                        + "map_y DOUBLE NULL,"
                        + "area_km2 DECIMAL(8,2) NULL,"
                        + "risk_level VARCHAR(16) NULL,"
                        + "description VARCHAR(255) NULL,"
                        + "created_at DATETIME NOT NULL,"
                        + "UNIQUE KEY uk_wh_habitat_code (habitat_code),"
                        + "KEY idx_wh_zone_id (zone_id)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );

        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS environment_record ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "zone_id BIGINT NULL,"
                        + "zone_name VARCHAR(64) NULL,"
                        + "record_time DATETIME NOT NULL,"
                        + "temperature DECIMAL(5,2) NULL,"
                        + "humidity DECIMAL(5,2) NULL,"
                        + "soil_moisture DECIMAL(5,2) NULL,"
                        + "wind_speed DECIMAL(5,2) NULL,"
                        + "rainfall_mm DECIMAL(6,2) NULL,"
                        + "KEY idx_er_zone_id_time (zone_id, record_time)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );

        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS drone_mission ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "mission_code VARCHAR(32) NOT NULL,"
                        + "drone_id VARCHAR(32) NOT NULL,"
                        + "zone_id BIGINT NULL,"
                        + "zone_name VARCHAR(64) NULL,"
                        + "target_node_code VARCHAR(32) NULL,"
                        + "mission_type VARCHAR(32) NOT NULL,"
                        + "state VARCHAR(32) NOT NULL,"
                        + "alarm_id BIGINT NULL,"
                        + "target_lat DECIMAL(10,7) NULL,"
                        + "target_lng DECIMAL(10,7) NULL,"
                        + "target_map_x DOUBLE NULL,"
                        + "target_map_y DOUBLE NULL,"
                        + "confirmed_fire TINYINT NOT NULL DEFAULT 0,"
                        + "result_note VARCHAR(255) NULL,"
                        + "dispatch_time DATETIME NULL,"
                        + "arrive_time DATETIME NULL,"
                        + "return_time DATETIME NULL,"
                        + "complete_time DATETIME NULL,"
                        + "created_at DATETIME NOT NULL,"
                        + "UNIQUE KEY uk_dm_mission_code (mission_code),"
                        + "KEY idx_dm_alarm_id (alarm_id)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );

        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS ecological_followup ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "alarm_id BIGINT NOT NULL,"
                        + "asset_type VARCHAR(32) NOT NULL,"
                        + "asset_id BIGINT NOT NULL,"
                        + "asset_code VARCHAR(32) NOT NULL,"
                        + "asset_name VARCHAR(64) NOT NULL,"
                        + "zone_id BIGINT NULL,"
                        + "zone_name VARCHAR(64) NULL,"
                        + "state VARCHAR(32) NOT NULL,"
                        + "followup_note VARCHAR(255) NULL,"
                        + "handler VARCHAR(64) NULL,"
                        + "due_time DATETIME NULL,"
                        + "completed_time DATETIME NULL,"
                        + "created_at DATETIME NOT NULL,"
                        + "KEY idx_ef_alarm_id (alarm_id),"
                        + "KEY idx_ef_asset (asset_type, asset_id)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
    }


    /* ==================================================
       生态资源种子数据（幂等）
       ================================================== */

    private void seedZones() {

        seedZone("Z01", "北部核心保护区",
                "主峰北侧原始针阔混交林，古树与核心栖息地集中区",
                8.5, 300, 180, "HIGH");
        seedZone("Z02", "东部防火通道区",
                "东侧防火隔离带与巡护通道，车辆/人员通行主要通道",
                6.2, 560, 180, "MEDIUM");
        seedZone("Z03", "西部生态保育区",
                "西部阔叶林保育区，植被茂密、林下可燃物较多",
                7.8, 300, 420, "HIGH");
        seedZone("Z04", "南部生态涵养区",
                "南部水源涵养林与湿地边缘，湿度较高",
                9.1, 560, 420, "MEDIUM");
    }

    private void seedZone(String code, String name, String desc,
                          double area, double mapX, double mapY,
                          String risk) {

        jdbcTemplate.update(
                "INSERT INTO forest_zone "
                        + "(zone_code, zone_name, description, area_km2, "
                        + " map_center_x, map_center_y, risk_level, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, NOW()) "
                        + "ON DUPLICATE KEY UPDATE "
                        + "zone_name = VALUES(zone_name), "
                        + "description = VALUES(description), "
                        + "area_km2 = VALUES(area_km2), "
                        + "map_center_x = VALUES(map_center_x), "
                        + "map_center_y = VALUES(map_center_y), "
                        + "risk_level = VALUES(risk_level)",
                code, name, desc, area, mapX, mapY, risk
        );
    }

    private void seedTrees() {

        seedTree("AT-001", "北部古松", "Pinus tabuliformis", "松科·松属",
                300, "一级", 1, "FS-N-001",
                28.1850000, 113.1050000, 310, 120, 95.2, 23.5,
                "健康", "北部核心保护区标志性古松，距今约300年");
        seedTree("AT-002", "云杉王", "Picea asperata", "松科·云杉属",
                220, "一级", 1, "FS-N-002",
                28.1780000, 113.0960000, 230, 150, 78.0, 32.0,
                "健康", "林缘高挺云杉，树冠浓密");
        seedTree("AT-003", "樟树爷爷", "Cinnamomum camphora", "樟科·樟属",
                180, "二级", 2, "FS-N-026",
                28.1820000, 113.1450000, 570, 110, 65.5, 18.2,
                "亚健康", "防火通道旁百年樟树，局部枝条生长势弱");
        seedTree("AT-004", "栎树王", "Quercus acutissima", "壳斗科·栎属",
                150, "二级", 3, "FS-N-048",
                28.1520000, 113.1020000, 310, 380, 52.3, 20.0,
                "健康", "西部保育区优势栎树");
        seedTree("AT-005", "银杏古树", "Ginkgo biloba", "银杏科·银杏属",
                260, "一级", 4, "FS-N-070",
                28.1480000, 113.1420000, 540, 400, 88.0, 26.4,
                "健康", "南部涵养区珍稀银杏，秋季景观资源");
    }

    private void seedTree(String code, String name, String scientific,
                          String species, int age, String level, int zoneId,
                          String nodeCode, double lat, double lng,
                          double mapX, double mapY,
                          double diameter, double height,
                          String health, String desc) {

        jdbcTemplate.update(
                "INSERT INTO ancient_tree "
                        + "(tree_code, tree_name, scientific_name, species, "
                        + " age_years, protection_level, zone_id, zone_name, "
                        + " node_code, latitude, longitude, map_x, map_y, "
                        + " diameter_cm, height_m, health_state, description, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW()) "
                        + "ON DUPLICATE KEY UPDATE "
                        + "tree_name = VALUES(tree_name), "
                        + "scientific_name = VALUES(scientific_name), "
                        + "species = VALUES(species), "
                        + "age_years = VALUES(age_years), "
                        + "protection_level = VALUES(protection_level), "
                        + "zone_id = VALUES(zone_id), "
                        + "zone_name = VALUES(zone_name), "
                        + "node_code = VALUES(node_code), "
                        + "latitude = VALUES(latitude), "
                        + "longitude = VALUES(longitude), "
                        + "map_x = VALUES(map_x), "
                        + "map_y = VALUES(map_y), "
                        + "diameter_cm = VALUES(diameter_cm), "
                        + "height_m = VALUES(height_m), "
                        + "health_state = VALUES(health_state), "
                        + "description = VALUES(description)",
                code, name, scientific, species, age, level, zoneId,
                zoneNameFor(zoneId), nodeCode, lat, lng, mapX, mapY,
                diameter, height, health, desc
        );
    }

    private void seedInspections() {

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ancient_tree_inspection",
                Long.class);

        if (count != null && count > 0) {
            return;
        }

        jdbcTemplate.update(
                "INSERT INTO ancient_tree_inspection "
                        + "(tree_id, tree_code, inspection_date, inspector, "
                        + " health_state, pest_risk, description, created_at) "
                        + "VALUES ((SELECT id FROM ancient_tree WHERE tree_code = 'AT-001'),"
                        + " 'AT-001', '2026-08-01 09:30:00', '王巡护', "
                        + " 'HEALTHY', '无', '树皮完整，无明显枯枝', NOW())"
        );

        jdbcTemplate.update(
                "INSERT INTO ancient_tree_inspection "
                        + "(tree_id, tree_code, inspection_date, inspector, "
                        + " health_state, pest_risk, description, created_at) "
                        + "VALUES ((SELECT id FROM ancient_tree WHERE tree_code = 'AT-003'),"
                        + " 'AT-003', '2026-07-20 15:00:00', '李巡护', "
                        + " 'FAIR', '低', '部分枝条生长势弱，建议增加巡查频次', NOW())"
        );

        log.info("已播种古树巡护检查样例记录 2 条");
    }

    private void seedHabitats() {

        seedHabitat("WH-001", "北部林冠鸟类栖息地", 1,
                "CORE", "勺鸡、灰喜鹊、松雀鹰",
                28.1820000, 113.1020000, 290, 140, 1.8, "HIGH",
                "北部核心区成熟林冠层，重点鸟类繁殖与停歇地");
        seedHabitat("WH-002", "东侧溪谷兽类通道", 1,
                "HIGH", "獐子、野猪、貉",
                28.1770000, 113.1080000, 350, 210, 1.2, "HIGH",
                "溪谷两侧兽类迁徙与饮水通道");
        seedHabitat("WH-003", "西部灌丛栖息地", 3,
                "HIGH", "灰林鸮、长尾山雀、中华竹鼠",
                28.1530000, 113.0980000, 280, 430, 1.5, "HIGH",
                "西部灌丛与林缘地带，夜行性鸟类停歇地");
        seedHabitat("WH-004", "南部湿地鸟类栖息地", 4,
                "MEDIUM", "白鹭、苍鹭、黑水鸡",
                28.1460000, 113.1400000, 530, 460, 2.3, "MEDIUM",
                "南部湿地与浅水区，水鸟集群栖息地");
    }

    private void seedHabitat(String code, String name, int zoneId,
                             String level, String species,
                             double lat, double lng,
                             double mapX, double mapY, double area,
                             String risk, String desc) {

        jdbcTemplate.update(
                "INSERT INTO wildlife_habitat "
                        + "(habitat_code, habitat_name, zone_id, zone_name, "
                        + " protection_level, species_keywords, latitude, longitude, "
                        + " map_x, map_y, area_km2, risk_level, description, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW()) "
                        + "ON DUPLICATE KEY UPDATE "
                        + "habitat_name = VALUES(habitat_name), "
                        + "zone_id = VALUES(zone_id), "
                        + "zone_name = VALUES(zone_name), "
                        + "protection_level = VALUES(protection_level), "
                        + "species_keywords = VALUES(species_keywords), "
                        + "latitude = VALUES(latitude), "
                        + "longitude = VALUES(longitude), "
                        + "map_x = VALUES(map_x), "
                        + "map_y = VALUES(map_y), "
                        + "area_km2 = VALUES(area_km2), "
                        + "risk_level = VALUES(risk_level), "
                        + "description = VALUES(description)",
                code, name, zoneId, zoneNameFor(zoneId), level, species,
                lat, lng, mapX, mapY, area, risk, desc
        );
    }

    /**
     * 播种默认森林巡护员账号（幂等）：
     * 用户名 patrol / 密码 123456 / 角色 resident / 绑定 Z01 北部核心保护区。
     *
     * 巡护员内部复用 resident 角色，以 sys_user.zone_id 表示巡护分区，
     * 不再单独开放 patrol 角色（避免角色体系膨胀）。
     * 密码以明文落库，登录成功时会被自动升级为 BCrypt（兼容旧账号机制）。
     * 历史居民 / 管理员账号保持不变，自助注册已关闭。
     */
    private void seedPatrolUser() {

        if (!tableExists("sys_user") || !columnExists("sys_user", "zone_id")) {
            return;
        }

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = 'patrol'",
                Long.class);

        if (count != null && count > 0) {
            // 已存在：把历史 patrol 角色修正为 resident，并确保绑定分区
            jdbcTemplate.update(
                    "UPDATE sys_user SET role = 'resident', zone_id = 1 "
                            + "WHERE username = 'patrol' AND role = 'patrol'"
            );
            return;
        }

        try {
            jdbcTemplate.update(
                    "INSERT INTO sys_user "
                            + "(username, password, role, zone_id, phone, job_num) "
                            + "VALUES ('patrol', '123456', 'resident', 1, "
                            + "'13800000001', 'XH-001')"
            );
            log.info("已播种默认巡护员账号 patrol / 123456（角色 resident，绑定 Z01）");
        } catch (Exception e) {
            log.warn("播种默认巡护员账号失败，继续启动：{}", e.getMessage());
        }
    }

    /**
     * 迁移历史 patrol 角色账号到 resident（幂等）。
     *
     * 早期版本为巡护员单独开放了 patrol 角色；本轮回退后巡护员
     * 内部统一使用 resident 角色，zone_id 表示巡护分区。这里把
     * 数据库里残留的 patrol 角色行全部迁移为 resident。
     */
    private void migratePatrolRole() {

        if (!tableExists("sys_user")) {
            return;
        }

        try {
            int updated = jdbcTemplate.update(
                    "UPDATE sys_user SET role = 'resident' WHERE role = 'patrol'"
            );

            if (updated > 0) {
                log.info(
                        "已将 {} 个历史 patrol 角色账号迁移为 resident"
                                + "（巡护员以 zone_id 区分）",
                        updated
                );
            }
        } catch (Exception e) {
            log.warn("迁移历史 patrol 角色失败，继续启动：{}", e.getMessage());
        }
    }


    /**
     * DEMO 环境监测记录：每个分区 3 条历史记录（仅用于演示，幂等）。
     */
    private void seedEnvironmentRecords() {

        int zone = 1;
        String name = "北部核心保护区";
        double[][] z1 = {
                {28.5, 38.2, 32.0, 4.8, 0.0},
                {26.5, 45.2, 38.0, 3.2, 0.0},
                {24.8, 52.6, 42.5, 2.4, 0.2}
        };
        seedEnvRow(zone, name, "2026-08-31 06:00:00", z1[0]);
        seedEnvRow(zone, name, "2026-08-31 12:00:00", z1[1]);
        seedEnvRow(zone, name, "2026-08-31 18:00:00", z1[2]);

        zone = 2;
        name = "东部防火通道区";
        double[][] z2 = {
                {29.6, 34.0, 28.0, 5.6, 0.0},
                {27.8, 40.5, 33.5, 4.1, 0.0},
                {26.0, 46.8, 37.0, 3.0, 0.0}
        };
        seedEnvRow(zone, name, "2026-08-31 06:00:00", z2[0]);
        seedEnvRow(zone, name, "2026-08-31 12:00:00", z2[1]);
        seedEnvRow(zone, name, "2026-08-31 18:00:00", z2[2]);

        zone = 3;
        name = "西部生态保育区";
        double[][] z3 = {
                {27.9, 41.5, 36.0, 4.0, 0.0},
                {25.9, 48.6, 41.2, 2.8, 0.0},
                {24.2, 55.0, 45.8, 2.0, 0.4}
        };
        seedEnvRow(zone, name, "2026-08-31 06:00:00", z3[0]);
        seedEnvRow(zone, name, "2026-08-31 12:00:00", z3[1]);
        seedEnvRow(zone, name, "2026-08-31 18:00:00", z3[2]);

        zone = 4;
        name = "南部生态涵养区";
        double[][] z4 = {
                {27.0, 46.0, 42.0, 3.0, 0.2},
                {25.2, 52.4, 46.0, 2.1, 0.5},
                {23.6, 58.2, 50.2, 1.6, 0.8}
        };
        seedEnvRow(zone, name, "2026-08-31 06:00:00", z4[0]);
        seedEnvRow(zone, name, "2026-08-31 12:00:00", z4[1]);
        seedEnvRow(zone, name, "2026-08-31 18:00:00", z4[2]);
    }

    private void seedEnvRow(int zoneId, String zoneName, String time,
                            double[] data) {

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM environment_record "
                        + "WHERE zone_id = ? AND record_time = ?",
                Long.class,
                zoneId,
                time
        );

        if (count != null && count > 0) {
            return;
        }

        jdbcTemplate.update(
                "INSERT INTO environment_record "
                        + "(zone_id, zone_name, record_time, temperature, "
                        + " humidity, soil_moisture, wind_speed, rainfall_mm) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                zoneId, zoneName, time, data[0], data[1], data[2],
                data[3], data[4]
        );
    }


    /* ==================================================
       森林监测节点播种
       ================================================== */

    /**
     * 播种/修正森林监测节点：
     * - 1001（真实硬件）只更新森林字段，保留 building/floor/room 历史兼容；
     * - 1002~1090（DEMO）不存在则插入，存在则更新森林字段，
     *   绝不覆盖 source_type（保持 REAL/DEMO 严格隔离）。
     */
    private void seedForestNodes() {

        for (long deviceId = MIN_DEVICE_ID;
             deviceId <= MAX_DEVICE_ID; deviceId++) {

            NodePlan plan = planNode(deviceId);

            if (deviceId == REAL_NODE_DEVICE_ID) {

                jdbcTemplate.update(
                        "UPDATE device SET zone_id = ?, node_code = ?, "
                                + "node_name = ?, latitude = ?, longitude = ?, "
                                + "map_x = ?, map_y = ? "
                                + "WHERE device_id = ?",
                        plan.zoneId(), plan.nodeCode(), plan.nodeName(),
                        plan.latitude(), plan.longitude(),
                        plan.mapX(), plan.mapY(), deviceId
                );
            } else {

                jdbcTemplate.update(
                        "INSERT INTO device "
                                + "(device_id, status, source_type, zone_id, "
                                + " node_code, node_name, latitude, longitude, "
                                + " map_x, map_y, health_status, "
                                + " last_report_time, consecutive_failures) "
                                + "VALUES (?, 1, 'DEMO', ?, ?, ?, ?, ?, ?, ?, "
                                + " 'STALE', NULL, 0) "
                                + "ON DUPLICATE KEY UPDATE "
                                + "zone_id = VALUES(zone_id), "
                                + "node_code = VALUES(node_code), "
                                + "node_name = VALUES(node_name), "
                                + "latitude = VALUES(latitude), "
                                + "longitude = VALUES(longitude), "
                                + "map_x = VALUES(map_x), "
                                + "map_y = VALUES(map_y), "
                                + "status = VALUES(status)",
                        deviceId, plan.zoneId(), plan.nodeCode(),
                        plan.nodeName(), plan.latitude(), plan.longitude(),
                        plan.mapX(), plan.mapY()
                );
            }
        }

        log.info(
                "森林监测节点已就绪：1001=FS-N-001（REAL），"
                        + "1002~1090=FS-N-002~FS-N-090（DEMO），"
                        + "已按分区映射地图坐标"
        );
    }


    /* ==================================================
       历史数据回填
       ================================================== */

    private void backfillLegacyAlarmSceneType() {

        int updated = jdbcTemplate.update(
                "UPDATE alarm SET scene_type = 'DORM_LEGACY' "
                        + "WHERE scene_type IS NULL OR scene_type = ''"
        );

        if (updated > 0) {
            log.info(
                    "已将 {} 条历史告警 scene_type 回填为 DORM_LEGACY",
                    updated
            );
        }
    }


    /* ==================================================
       节点规划（纯函数，便于单元测试）
       ================================================== */

    /**
     * 设备ID → 森林分区ID（1=Z01 ... 4=Z04）。
     */
    static int zoneIdForDevice(long deviceId) {

        if (deviceId < MIN_DEVICE_ID || deviceId > MAX_DEVICE_ID) {
            throw new IllegalArgumentException(
                    "非森林监测节点设备ID无法推导区域：" + deviceId
            );
        }

        if (deviceId <= 1024) return 1;
        if (deviceId <= 1046) return 2;
        if (deviceId <= 1068) return 3;
        return 4;
    }

    /**
     * 设备ID → 监测节点编号：1001→FS-N-001 ... 1090→FS-N-090。
     */
    static String nodeCodeForDevice(long deviceId) {

        return "FS-N-"
                + String.format("%03d", deviceId - 1000);
    }

    /**
     * 分区ID → 分区名称。
     */
    static String zoneNameFor(int zoneId) {

        switch (zoneId) {
            case 1: return "北部核心保护区";
            case 2: return "东部防火通道区";
            case 3: return "西部生态保育区";
            case 4: return "南部生态涵养区";
            default:
                throw new IllegalArgumentException(
                        "未知森林分区ID：" + zoneId
                );
        }
    }

    /**
     * 设备在该分区内的序号（0 起）。
     */
    static int indexInZone(long deviceId) {

        int zoneId = zoneIdForDevice(deviceId);
        long offset;

        switch (zoneId) {
            case 1: offset = deviceId - 1001; break;
            case 2: offset = deviceId - 1025; break;
            case 3: offset = deviceId - 1047; break;
            default: offset = deviceId - 1069; break;
        }

        return (int) offset;
    }

    /**
     * 生成一台监测节点的完整规划（纯函数）。
     */
    static NodePlan planNode(long deviceId) {

        int zoneId = zoneIdForDevice(deviceId);
        double[] base = ZONE_BASE[zoneId - 1];

        int idxInZone = indexInZone(deviceId);
        int row = idxInZone / 5;
        int col = idxInZone % 5;

        double baseX = base[0];
        double baseY = base[1];
        double baseLat = base[2];
        double baseLng = base[3];

        double mapX = round1(baseX + (col - 2) * 70);
        double mapY = round1(baseY + (row - 1) * 70);
        double lat = baseLat - (row - 1) * 0.006;
        double lng = baseLng + (col - 2) * 0.008;

        String nodeCode = nodeCodeForDevice(deviceId);

        return new NodePlan(
                deviceId,
                zoneId,
                nodeCode,
                nodeNameFor(deviceId, zoneId, row, col),
                BigDecimal.valueOf(lat).setScale(7, RoundingMode.HALF_UP),
                BigDecimal.valueOf(lng).setScale(7, RoundingMode.HALF_UP),
                mapX,
                mapY
        );
    }

    /**
     * 节点名称（不重复分区名 / 节点编号，节点编号为唯一编码）：
     * 真实节点用点位描述命名；DEMO 节点按分区特征 + 网格定位命名，
     * 例如"古松林A2监测点"。
     */
    static String nodeNameFor(long deviceId, int zoneId, int row, int col) {

        if (deviceId == REAL_NODE_DEVICE_ID) {
            return "北部古松周边监测点";
        }

        return zoneFeatureFor(zoneId)
                + ("ABCDE".charAt(row))
                + (col + 1)
                + "监测点";
    }

    /**
     * 分区特征词：古松林 / 通道林缘 / 保育林带 / 涵养溪谷。
     */
    static String zoneFeatureFor(int zoneId) {

        switch (zoneId) {
            case 1: return "古松林";
            case 2: return "通道林缘";
            case 3: return "保育林带";
            case 4: return "涵养溪谷";
            default:
                throw new IllegalArgumentException(
                        "未知森林分区ID：" + zoneId
                );
        }
    }

    private static double round1(double value) {

        return Math.round(value * 10) / 10.0;
    }


    /**
     * 一台森林监测节点的规划结果。
     */
    record NodePlan(
            long deviceId,
            int zoneId,
            String nodeCode,
            String nodeName,
            BigDecimal latitude,
            BigDecimal longitude,
            double mapX,
            double mapY) {
    }


    /* ==================================================
       通用辅助
       ================================================== */

    private boolean tableExists(String tableName) {

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                tableName
        );

        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName,
                columnName
        );

        return count != null && count > 0;
    }

    private void addColumnIfMissing(String tableName, String columnName,
                                    String columnDefinition) {

        if (columnExists(tableName, columnName)) {
            return;
        }

        jdbcTemplate.execute(
                "ALTER TABLE " + tableName
                        + " ADD COLUMN " + columnName
                        + " " + columnDefinition
        );

        log.info("已为 {} 表补充列 {}", tableName, columnName);
    }

    private void ensureUniqueIndex(String tableName, String indexName,
                                   String columnName) {

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Integer.class,
                tableName,
                indexName
        );

        if (count != null && count > 0) {
            return;
        }

        try {
            jdbcTemplate.execute(
                    "CREATE UNIQUE INDEX " + indexName
                            + " ON " + tableName
                            + " (" + columnName + ")"
            );
            log.info("已创建唯一索引：{}.{} ON {}", tableName, indexName, columnName);
        } catch (Exception e) {
            log.warn("创建唯一索引失败，继续启动：{}.{}", tableName, indexName);
        }
    }
}
