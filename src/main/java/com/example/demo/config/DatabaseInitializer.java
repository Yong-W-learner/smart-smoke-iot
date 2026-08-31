package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单设备 → 多设备 数据库升级 + 设备ID规范化。
 *
 * 启动时自动执行（幂等）：
 *
 * 1. 为 device 表补充 iot_device_id、source_type 列
 * 2. 设备ID规范化迁移（必须在去重 / 唯一索引 / 播种之前）：
 *    - 真实硬件 1 → 1001（1栋1层101室，source_type=REAL 完整保留，
 *      同步迁移 smoke_record / alarm / alarm_event_log 引用）；
 *    - 旧 DEMO 503 → 1083（3栋5层503室）；
 *    - 清理旧特殊设备 101（其寝室位置由真实设备 1001 接管）。
 * 3. 为 iot_device_id 创建唯一索引（允许多个 NULL）
 * 4. 历史设备回填 source_type = REAL，回填真实设备的 iot_device_id
 * 5. 清理历史 REAL/DEMO 寝室重复迁移：
 *    扫描 (building,floor,room) 重复位置，REAL 优先保留、
 *    冲突 DEMO 清理，随后创建位置唯一索引
 *    （保证"一个寝室只对应一台设备"）
 *
 * 注意：不再调用 seedDormitoryFleet() 整栋宿舍播种——
 * 设备 1002~1090 的森林监测节点播种由
 * {@link ForestDatabaseInitializer} 负责（1001 保持 REAL 不变）。
 * 宿舍播种纯函数（planDormitoryFleet / deviceIdFor / positionKey）
 * 仅保留供历史单元测试引用，不再参与运行期播种。
 *
 * 不再存在 101 / 503 / 1 等特殊设备编号。
 *
 * 不修改现有表结构，只追加新列 / 新行 / 更新历史数据。
 */
@Component
public class DatabaseInitializer {

    private static final Logger log =
            LoggerFactory.getLogger(DatabaseInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    /**
     * 兼容旧配置的华为云设备ID，
     * 仅用于回填真实设备的 iot_device_id。
     */
    @Value("${huawei.iot.deviceId:}")
    private String seedIotDeviceId;

    /**
     * 真实硬件在数据库中的规范化设备ID。
     *
     * 迁移后固定为 1001（1栋1层101室）。
     */
    @Value("${huawei.iot.localDbDeviceId:1001}")
    private Long localDbDeviceId;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {

        if (!tableExists("device")) {

            log.warn(
                    "device 表不存在，跳过多设备数据库初始化。"
                            + "请先手动创建核心表。"
            );

            return;
        }

        addColumnIfMissing(
                "device",
                "iot_device_id",
                "VARCHAR(128) NULL"
        );

        addColumnIfMissing(
                "device",
                "source_type",
                "VARCHAR(16) NOT NULL DEFAULT 'REAL'"
        );

        /*
         * 设备ID规范化迁移必须先于：
         * 位置去重 / 位置唯一索引 / 整栋宿舍播种。
         *
         * 重复启动幂等：旧ID（1/503/101）不存在时自动跳过。
         */
        try {
            migrateLegacyDeviceIds();
        } catch (Exception e) {
            log.warn(
                    "设备ID规范化迁移失败，跳过（不影响后续初始化）：{}",
                    e.toString()
            );
        }

        /*
         * 创建唯一索引之前，先把历史空字符串规范化为 NULL。
         *
         * 存量数据里 iot_device_id 可能为 ''（空字符串），
         * MySQL 唯一索引会把这些 '' 视为重复值，
         * 导致 CREATE UNIQUE INDEX 失败。
         *
         * 而 NULL 在 MySQL 唯一索引中允许多个，
         * 天然兼容"未绑定 IoTDA 的设备"。
         */
        normalizeEmptyToNull(
                "device",
                "iot_device_id"
        );

        ensureUniqueIndex(
                "device",
                "uk_device_iot_device_id",
                "iot_device_id"
        );

        backfillExistingDevices();

        /*
         * 清理历史 REAL/DEMO 重复迁移：
         * 先扫描并清理重复寝室位置，再创建位置唯一索引。
         */
        deduplicateOccupiedPositions();

        ensureUniqueIndex(
                "device",
                "uk_device_building_floor_room",
                "building, floor, room"
        );

        /*
         * 不再整栋宿舍播种。
         *
         * 设备 1002~1090 的森林监测节点播种由
         * ForestDatabaseInitializer 负责（保持 1001=REAL，
         * 1002~1090=DEMO，映射到森林分区）。
         */
        log.info(
                "设备ID规范化与多设备数据库初始化完成："
                        + "真实硬件ID={}（1栋1层101室），"
                        + "旧特殊编号 1/101/503 已迁移或清理，"
                        + "重复寝室位置已按 REAL 优先去重并加位置唯一索引，"
                        + "森林监测节点播种由 ForestDatabaseInitializer 接管",
                localDbDeviceId
        );
    }


    /**
     * 生成整栋宿舍播种计划（纯函数，仅供历史单元测试引用）。
     *
     * 运行期已不再执行宿舍播种（见 init() 注释），
     * 该方法保留用于验证设备ID映射规则的一致性。
     *
     * @param occupiedPositions 已占用的"楼栋:楼层:房间"位置集合
     * @return 按顺序的播种计划，positionOccupied=true 表示该位置已有设备
     */
    static List<FleetEntry> planDormitoryFleet(
            Set<String> occupiedPositions) {

        List<FleetEntry> plan = new ArrayList<>();

        for (int building = 1; building <= 3; building++) {

            for (int floor = 1; floor <= 6; floor++) {

                for (int slot = 1; slot <= 5; slot++) {

                    int room = floor * 100 + slot;

                    long deviceId =
                            deviceIdFor(building, floor, room);

                    boolean occupied =
                            occupiedPositions.contains(
                                    positionKey(building, floor, room)
                            );

                    plan.add(
                            new FleetEntry(
                                    deviceId,
                                    building,
                                    floor,
                                    room,
                                    occupied
                            )
                    );
                }
            }
        }

        return plan;
    }


    /**
     * 统一寝室设备ID映射（纯函数，全局唯一入口）。
     *
     * 固定规则：3 栋楼 × 6 层 × 每层 5 间 = 90 个寝室位置，
     * device_id = 1001 ~ 1090。
     *
     * - 1栋1层101室 = 1001（真实硬件）
     * - 3栋5层503室 = 1083（默认标准 DEMO 演示寝室）
     * - 其余位置依序推导
     *
     * @return deviceIdFor(building, floor, room)
     * @throws IllegalArgumentException 非标准寝室位置无法推导
     */
    static long deviceIdFor(int building, int floor, int room) {

        int slot = room - floor * 100;

        if (building < 1 || building > 3
                || floor < 1 || floor > 6
                || slot < 1 || slot > 5) {

            throw new IllegalArgumentException(
                    "非标准寝室位置无法推导设备ID："
                            + building + "栋" + floor + "层" + room + "室"
            );
        }

        return 1001L
                + (long) (building - 1) * 30
                + (long) (floor - 1) * 5
                + (slot - 1);
    }


    /**
     * 位置唯一键：楼栋:楼层:房间。
     */
    static String positionKey(
            int building,
            int floor,
            int room) {

        return building + ":" + floor + ":" + room;
    }


    /**
     * 把某列的空字符串统一规范化为 NULL。
     *
     * 必须发生在创建唯一索引之前，
     * 避免历史空字符串破坏唯一性。
     */
    private void normalizeEmptyToNull(
            String tableName,
            String columnName) {

        int updated =
                jdbcTemplate.update(
                        emptyToNullSql(tableName, columnName)
                );

        if (updated > 0) {

            log.info(
                    "已将 {}.{} 中 {} 条空字符串规范化为 NULL",
                    tableName,
                    columnName,
                    updated
            );
        }
    }


    /**
     * 空字符串 -> NULL 的规范化 SQL（纯函数，便于单元测试）。
     */
    static String emptyToNullSql(
            String tableName,
            String columnName) {

        return "UPDATE " + tableName
                + " SET " + columnName + " = NULL "
                + "WHERE " + columnName + " = ''";
    }


    /**
     * 整栋宿舍播种计划中的一台设备。
     */
    static class FleetEntry {

        final long deviceId;

        final int building;

        final int floor;

        final int room;

        /**
         * true 表示该寝室位置已被其它设备占用，不应重复创建。
         */
        final boolean positionOccupied;

        FleetEntry(
                long deviceId,
                int building,
                int floor,
                int room,
                boolean positionOccupied) {

            this.deviceId = deviceId;
            this.building = building;
            this.floor = floor;
            this.room = room;
            this.positionOccupied = positionOccupied;
        }
    }


    /**
     * 判断表是否存在。
     */
    private boolean tableExists(String tableName) {

        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.TABLES "
                                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                        Integer.class,
                        tableName
                );

        return count != null && count > 0;
    }


    /**
     * 列不存在时补充列。
     */
    private void addColumnIfMissing(
            String tableName,
            String columnName,
            String columnDefinition) {

        if (columnExists(tableName, columnName)) {

            log.debug(
                    "列已存在，跳过：{}.{}",
                    tableName,
                    columnName
            );

            return;
        }

        jdbcTemplate.execute(
                "ALTER TABLE " + tableName
                        + " ADD COLUMN " + columnName
                        + " " + columnDefinition
        );

        log.info(
                "已为 {} 表补充列 {}",
                tableName,
                columnName
        );
    }


    /**
     * 判断列是否存在。
     */
    private boolean columnExists(
            String tableName,
            String columnName) {

        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.COLUMNS "
                                + "WHERE TABLE_SCHEMA = DATABASE() "
                                + "AND TABLE_NAME = ? "
                                + "AND COLUMN_NAME = ?",
                        Integer.class,
                        tableName,
                        columnName
                );

        return count != null && count > 0;
    }


    /**
     * 历史设备回填：
     *
     * source_type 为空的设备置 REAL；
     * 真实设备（localDbDeviceId）的 iot_device_id 用兼容种子值回填。
     */
    private void backfillExistingDevices() {

        int updatedType =
                jdbcTemplate.update(
                        "UPDATE device SET source_type = 'REAL' "
                                + "WHERE source_type IS NULL OR source_type = ''"
                );

        if (updatedType > 0) {
            log.info(
                    "已为 {} 台历史设备回填 source_type=REAL",
                    updatedType
            );
        }

        if (seedIotDeviceId != null
                && !seedIotDeviceId.trim().isEmpty()) {

            int updatedIot =
                    jdbcTemplate.update(
                            "UPDATE device SET iot_device_id = ? "
                                    + "WHERE device_id = ? "
                                    + "AND (iot_device_id IS NULL OR iot_device_id = '')",
                            seedIotDeviceId.trim(),
                            localDbDeviceId
                    );

            if (updatedIot > 0) {
                log.info(
                        "已为真实设备(device_id={})回填 iot_device_id",
                        localDbDeviceId
                );
            }
        }
    }


    /**
     * 修复历史版本产生的"一个寝室位置对应多台设备"的重复数据。
     *
     * 场景：REAL/DEMO 迁移时可能在同一 (building, floor, room)
     * 重复创建设备（如真实设备与 DEMO 仿真设备占同一寝室）。
     *
     * 策略（REAL 优先）：
     * - 位置存在 REAL 设备：保留 device_id 最小的 REAL 设备，
     *   其余设备（含冲突 DEMO）全部清理；
     * - 位置无 REAL 设备：保留 device_id 最小的 DEMO 设备，
     *   其余重复 DEMO 清理。
     *
     * 清理后再由 init() 创建位置唯一索引，
     * 从数据库层面锁定"一个寝室只对应一台设备"。
     */
    private void deduplicateOccupiedPositions() {

        List<Map<String, Object>> duplicates =
                jdbcTemplate.queryForList(
                        "SELECT building, floor, room FROM device "
                                + "WHERE building IS NOT NULL "
                                + "AND floor IS NOT NULL "
                                + "AND room IS NOT NULL "
                                + "GROUP BY building, floor, room "
                                + "HAVING COUNT(*) > 1");

        for (Map<String, Object> row : duplicates) {

            Object b = row.get("building");
            Object f = row.get("floor");
            Object r = row.get("room");

            if (b == null || f == null || r == null) {
                continue;
            }

            int building = ((Number) b).intValue();
            int floor = ((Number) f).intValue();
            int room = ((Number) r).intValue();

            Long realAtPosition =
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM device "
                                    + "WHERE building = ? AND floor = ? AND room = ? "
                                    + "AND source_type = 'REAL'",
                            Long.class,
                            building,
                            floor,
                            room
                    );

            boolean hasReal =
                    realAtPosition != null && realAtPosition > 0;

            Long keeper =
                    jdbcTemplate.queryForObject(
                            "SELECT MIN(device_id) FROM device "
                                    + "WHERE building = ? AND floor = ? AND room = ? "
                                    + (hasReal ? "AND source_type = 'REAL'" : ""),
                            Long.class,
                            building,
                            floor,
                            room
                    );

            if (keeper == null) {
                continue;
            }

            int deleted =
                    jdbcTemplate.update(
                            "DELETE FROM device "
                                    + "WHERE building = ? AND floor = ? AND room = ? "
                                    + "AND device_id <> ?",
                            building,
                            floor,
                            room,
                            keeper
                    );

            if (deleted > 0) {
                log.warn(
                        "已清理重复寝室设备：位置={}栋{}层{}室，"
                                + "保留 device_id={}（REAL优先），清理 {} 台",
                        building,
                        floor,
                        room,
                        keeper,
                        deleted
                );
            }
        }
    }


    /* ==================================================
       设备ID规范化迁移（幂等）
       ================================================== */

    /**
     * 迁移旧特殊设备ID（必须在去重 / 位置唯一索引 / 播种之前执行）。
     *
     * 历史版本存在非规范编号：
     * - 1：真实硬件（BearPi，1栋1层101室）→ 迁移为规范ID 1001，
     *   source_type=REAL 与 iot_device_id / user_id / health_status /
     *   last_report_time 等数据完整保留；
     * - 503：旧 DEMO 地标（3栋5层503室）→ 迁移为规范ID 1083；
     * - 101：旧 DEMO 地标（1栋1层101室，其寝室位置由真实设备 1001 接管）
     *   → 连同测试历史一并清理。
     *
     * 重复启动幂等：旧ID不存在时自动跳过。
     */
    private void migrateLegacyDeviceIds() {

        migrateDeviceId(1L, 1001L);

        /*
         * 真实硬件固定位于 1栋1层101室。
         * 若旧数据未落位置，这里统一补齐。
         */
        jdbcTemplate.update(
                "UPDATE device SET building = ?, floor = ?, room = ? "
                        + "WHERE device_id = ?",
                1, 1, 101, 1001L
        );

        migrateDeviceId(503L, 1083L);

        removeLegacyDevice(101L);
    }


    /**
     * 把旧 device_id 迁移为规范 device_id（幂等）。
     *
     * - 旧ID不存在：跳过。
     * - 新ID已存在且为 DEMO：先安全清理该 DEMO 及其直接引用
     *   device_id 的历史数据（测试数据），再迁移；
     * - 新ID已存在且为 REAL：记录告警并跳过，绝不删除真实硬件数据。
     *
     * 迁移同时同步 smoke_record / alarm / alarm_event_log
     * 中直接引用旧 device_id 的历史数据；
     * alarm_feedback 通过 alarm_id 关联，不涉及 device_id，保持不变。
     */
    private void migrateDeviceId(long oldId, long newId) {

        if (!deviceExists(oldId)) {
            return;
        }

        if (deviceExists(newId)) {

            if (isRealDevice(newId)) {
                log.warn(
                        "迁移跳过：目标ID({})已是REAL设备，"
                                + "不删除真实硬件数据。旧ID={}",
                        newId,
                        oldId
                );
                return;
            }

            deleteDeviceAndHistory(newId);

            log.warn(
                    "已清理目标ID的DEMO设备及测试历史：device_id={}"
                            + "（为 {} → {} 迁移让位）",
                    newId,
                    oldId,
                    newId
            );
        }

        int refs = migrateReferences(oldId, newId);

        jdbcTemplate.update(
                "UPDATE device SET device_id = ? WHERE device_id = ?",
                newId,
                oldId
        );

        log.info(
                "已迁移设备ID：{} → {}（同步引用数据 {} 条）",
                oldId,
                newId,
                refs
        );
    }


    /**
     * 移除旧特殊设备（如 101）及其测试历史。
     */
    private void removeLegacyDevice(long deviceId) {

        if (!deviceExists(deviceId)) {
            return;
        }

        deleteDeviceAndHistory(deviceId);

        log.warn(
                "已移除旧特殊设备：device_id={}"
                        + "（其寝室位置已由真实设备 1001 接管）",
                deviceId
        );
    }


    private boolean deviceExists(long deviceId) {

        Long count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM device WHERE device_id = ?",
                        Long.class,
                        deviceId
                );

        return count != null && count > 0;
    }


    private boolean isRealDevice(long deviceId) {

        List<String> types =
                jdbcTemplate.queryForList(
                        "SELECT source_type FROM device WHERE device_id = ?",
                        String.class,
                        deviceId
                );

        return types.stream()
                .anyMatch("REAL"::equalsIgnoreCase);
    }


    /**
     * 同步迁移所有直接引用旧 device_id 的历史数据。
     *
     * @return 迁移的引用数据总条数
     */
    private int migrateReferences(long oldId, long newId) {

        int refs = 0;

        refs += updateReference("smoke_record", oldId, newId);
        refs += updateReference("alarm", oldId, newId);
        refs += updateReference("alarm_event_log", oldId, newId);

        return refs;
    }


    private int updateReference(String table, long oldId, long newId) {

        if (!tableExists(table)) {
            return 0;
        }

        return jdbcTemplate.update(
                "UPDATE " + table
                        + " SET device_id = ? WHERE device_id = ?",
                newId,
                oldId
        );
    }


    /**
     * 安全删除一台设备及其直接引用 device_id 的历史数据。
     * 表不存在时跳过对应删除，保证各版本表结构兼容。
     */
    private void deleteDeviceAndHistory(long deviceId) {

        if (tableExists("alarm_event_log")) {
            jdbcTemplate.update(
                    "DELETE FROM alarm_event_log WHERE device_id = ?",
                    deviceId
            );
        }

        if (tableExists("smoke_record")) {
            jdbcTemplate.update(
                    "DELETE FROM smoke_record WHERE device_id = ?",
                    deviceId
            );
        }

        if (tableExists("alarm")) {
            jdbcTemplate.update(
                    "DELETE FROM alarm WHERE device_id = ?",
                    deviceId
            );
        }

        jdbcTemplate.update(
                "DELETE FROM device WHERE device_id = ?",
                deviceId
        );
    }


    /**
     * 幂等创建唯一索引。
     *
     * MySQL 唯一索引允许多个 NULL，
     * 因此可以约束 iot_device_id 不能重复，
     * 同时允许未绑定的设备为 NULL。
     *
     * 已存在则跳过；创建失败（如存量重复数据）
     * 仅记警告，不阻断应用启动。
     */
    private void ensureUniqueIndex(
            String tableName,
            String indexName,
            String columnName) {

        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.STATISTICS "
                                + "WHERE TABLE_SCHEMA = DATABASE() "
                                + "AND TABLE_NAME = ? "
                                + "AND INDEX_NAME = ?",
                        Integer.class,
                        tableName,
                        indexName
                );

        if (count != null && count > 0) {
            log.debug(
                    "唯一索引已存在，跳过：{}.{}",
                    tableName,
                    indexName
            );
            return;
        }

        try {
            jdbcTemplate.execute(
                    "CREATE UNIQUE INDEX " + indexName
                            + " ON " + tableName
                            + " (" + columnName + ")"
            );
            log.info(
                    "已创建唯一索引：{}.{} ON {}",
                    tableName,
                    indexName,
                    columnName
            );
        } catch (Exception e) {
            log.warn(
                    "创建唯一索引失败（可能存量数据存在重复 iot_device_id），"
                            + "继续启动：{}.{}",
                    tableName,
                    indexName,
                    e
            );
        }
    }
}
