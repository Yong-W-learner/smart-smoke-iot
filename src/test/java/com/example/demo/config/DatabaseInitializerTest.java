package com.example.demo.config;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DatabaseInitializer 关键决策的纯函数测试。
 *
 * 覆盖：
 * - 整栋宿舍播种计划：已被占用的寝室位置（如真实设备 1001 / DEMO 1083）
 *   不再重复创建设备，其余寝室位置正常生成，
 *   保证"一个寝室只对应一台设备"。
 * - 统一设备ID映射 deviceIdFor(building,floor,room) = 1001~1090。
 * - 设备ID规范化迁移：1 → 1001（真实硬件）、503 → 1083、清理 101，
 *   同步迁移 smoke_record / alarm / alarm_event_log 引用，且幂等。
 * - iot_device_id 唯一索引前的空字符串 -> NULL 规范化 SQL。
 */
public class DatabaseInitializerTest {

    @Test
    void occupiedRoomsAreNotDuplicatedInFleetPlan() {

        /*
         * 1栋1层101室 = 1001（真实硬件），3栋5层503室 = 1083（DEMO），
         * 位置已被占用。
         */
        Set<String> occupied = Set.of(
                DatabaseInitializer.positionKey(1, 1, 101),
                DatabaseInitializer.positionKey(3, 5, 503)
        );

        List<DatabaseInitializer.FleetEntry> plan =
                DatabaseInitializer.planDormitoryFleet(occupied);

        assertEquals(90, plan.size());

        long duplicatedAt101 =
                plan.stream()
                        .filter(e -> e.deviceId == 1001L)
                        .count();
        long duplicatedAt503 =
                plan.stream()
                        .filter(e -> e.deviceId == 1083L)
                        .count();

        assertEquals(1, duplicatedAt101, "1001 必须仍在计划中（标记为占用）");
        assertEquals(1, duplicatedAt503, "1083 必须仍在计划中（标记为占用）");

        boolean skip101 =
                plan.stream().anyMatch(e ->
                        e.deviceId == 1001L && e.positionOccupied);
        boolean skip503 =
                plan.stream().anyMatch(e ->
                        e.deviceId == 1083L && e.positionOccupied);

        assertTrue(skip101, "1001 的位置（1栋1层101室）已被占用，应跳过创建");
        assertTrue(skip503, "1083 的位置（3栋5层503室）已被占用，应跳过创建");
    }

    @Test
    void nonOccupiedRoomsAreCreated() {

        Set<String> occupied = Set.of(
                DatabaseInitializer.positionKey(1, 1, 101),
                DatabaseInitializer.positionKey(3, 5, 503)
        );

        List<DatabaseInitializer.FleetEntry> plan =
                DatabaseInitializer.planDormitoryFleet(occupied);

        long creatable = plan.stream()
                .filter(e -> !e.positionOccupied)
                .count();

        assertEquals(88, creatable, "90 个位置去掉 2 个已占用位置后，应创建 88 台");
    }

    @Test
    void freshDatabaseHasNoConflicts() {

        List<DatabaseInitializer.FleetEntry> plan =
                DatabaseInitializer.planDormitoryFleet(Set.of());

        assertTrue(
                plan.stream().noneMatch(e -> e.positionOccupied),
                "全新数据库没有任何占用位置，全部寝室都应创建"
        );

        assertEquals(90, plan.size());
    }

    @Test
    void roomNumberDerivationIsStable() {

        List<DatabaseInitializer.FleetEntry> plan =
                DatabaseInitializer.planDormitoryFleet(Set.of());

        DatabaseInitializer.FleetEntry first =
                plan.get(0);

        assertEquals(1001L, first.deviceId);
        assertEquals(1, first.building);
        assertEquals(1, first.floor);
        assertEquals(101, first.room, "房间号 = 楼层*100 + 序号");

        DatabaseInitializer.FleetEntry building3Floor5Slot3 =
                plan.stream()
                        .filter(e -> e.deviceId == 1083L)
                        .findFirst()
                        .orElseThrow();

        assertEquals(3, building3Floor5Slot3.building);
        assertEquals(5, building3Floor5Slot3.floor);
        assertEquals(503, building3Floor5Slot3.room);
    }

    /* ==================================================
       统一设备ID映射 deviceIdFor
       ================================================== */

    @Test
    void deviceIdMappingIsUnified() {

        assertEquals(1001L, DatabaseInitializer.deviceIdFor(1, 1, 101),
                "1栋1层101室 = 1001（真实硬件）");
        assertEquals(1083L, DatabaseInitializer.deviceIdFor(3, 5, 503),
                "3栋5层503室 = 1083（默认标准 DEMO 演示寝室）");
        assertEquals(1005L, DatabaseInitializer.deviceIdFor(1, 1, 105));
        assertEquals(1031L, DatabaseInitializer.deviceIdFor(2, 1, 101));
        assertEquals(1090L, DatabaseInitializer.deviceIdFor(3, 6, 605),
                "3栋6层605室 = 最后一个编号 1090");
    }

    @Test
    void deviceIdForRejectsNonStandardRooms() {

        assertThrows(IllegalArgumentException.class,
                () -> DatabaseInitializer.deviceIdFor(9, 1, 101),
                "非标准楼栋无法推导设备ID");
        assertThrows(IllegalArgumentException.class,
                () -> DatabaseInitializer.deviceIdFor(1, 1, 106),
                "非标准房间号无法推导设备ID");
    }

    /**
     * 最终设备数量固定为 90 个寝室位置，
     * 设备ID连续为 1001~1090，不再出现 1 / 101 / 503 等特殊编号。
     */
    @Test
    void fleetCoversAll90StandardDeviceIds() {

        List<DatabaseInitializer.FleetEntry> plan =
                DatabaseInitializer.planDormitoryFleet(Set.of());

        assertEquals(90, plan.size());

        Set<Long> ids = plan.stream()
                .map(e -> e.deviceId)
                .collect(Collectors.toSet());

        assertEquals(90, ids.size(), "设备ID必须唯一");

        for (long id = 1001L; id <= 1090L; id++) {
            assertTrue(ids.contains(id), "缺少设备ID " + id);
        }

        assertFalse(ids.stream()
                        .anyMatch(id -> id == 1L || id == 101L || id == 503L),
                "不得再出现 1 / 101 / 503 等旧特殊编号");
    }

    @Test
    void emptyToNullSqlNormalizesOnlyEmptyStrings() {

        String sql = DatabaseInitializer.emptyToNullSql("device", "iot_device_id");

        assertEquals(
                "UPDATE device SET iot_device_id = NULL WHERE iot_device_id = ''",
                sql
        );

        assertFalse(sql.contains("NULL NULL"), "不能把 NULL 再次规范化为 NULL");
    }

    /**
     * 历史 REAL/DEMO 寝室重复迁移去重：
     * 扫描 (building,floor,room) 重复位置后，
     * REAL 优先保留、冲突 DEMO 清理。
     */
    @Test
    void deduplicateOccupiedPositionsKeepsRealAndCleansDemo() {

        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        DatabaseInitializer initializer =
                new DatabaseInitializer(jdbc);

        // 1栋1层101室存在重复设备
        when(jdbc.queryForList(anyString()))
                .thenReturn(List.of(
                        Map.of(
                                "building", 1,
                                "floor", 1,
                                "room", 101
                        )
                ));

        /*
         * queryForObject 调用顺序（varargs 需逐个匹配）：
         * 第 1 次（REAL 数量计数）→ 1（存在 REAL）；
         * 第 2 次（保留的 keeper 设备ID）→ 1001L。
         */
        when(jdbc.queryForObject(
                anyString(),
                org.mockito.ArgumentMatchers.eq(Long.class),
                any(), any(), any()))
                .thenReturn(1L, 1001L);

        when(jdbc.update(
                anyString(),
                any(), any(), any(), any()))
                .thenReturn(1);

        ReflectionTestUtils.invokeMethod(
                initializer,
                "deduplicateOccupiedPositions"
        );

        // 清理该位置 device_id <> 1001L 的冲突设备
        verify(jdbc).update(
                org.mockito.ArgumentMatchers.startsWith(
                        "DELETE FROM device"),
                any(), any(), any(), any()
        );
    }

    /* ==================================================
       设备ID规范化迁移
       ================================================== */

    /**
     * 真实硬件 1 完整迁移为 1001：
     * 同步迁移 smoke_record / alarm / alarm_event_log 引用，
     * 并把设备固定到 1栋1层101室。
     */
    @Test
    void realDevice1MigratesTo1001WithReferences() {

        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        DatabaseInitializer initializer =
                new DatabaseInitializer(jdbc);

        // 旧ID 1 存在；新ID 1001 不存在；503 / 101 不存在
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(1L)))
                .thenReturn(1L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(1001L)))
                .thenReturn(0L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(503L)))
                .thenReturn(0L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(101L)))
                .thenReturn(0L);

        // 三个引用表均存在
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("smoke_record")))
                .thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("alarm")))
                .thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("alarm_event_log")))
                .thenReturn(1);

        when(jdbc.update(startsWith("UPDATE smoke_record"), any(), any()))
                .thenReturn(2);
        when(jdbc.update(startsWith("UPDATE alarm SET"), any(), any()))
                .thenReturn(1);
        when(jdbc.update(startsWith("UPDATE alarm_event_log"), any(), any()))
                .thenReturn(1);
        when(jdbc.update(startsWith("UPDATE device SET device_id"), any(), any()))
                .thenReturn(1);
        when(jdbc.update(startsWith("UPDATE device SET building"), any(), any(), any(), any()))
                .thenReturn(1);

        ReflectionTestUtils.invokeMethod(
                initializer,
                "migrateLegacyDeviceIds"
        );

        verify(jdbc).update(startsWith("UPDATE smoke_record"), eq(1001L), eq(1L));
        verify(jdbc).update(startsWith("UPDATE alarm SET"), eq(1001L), eq(1L));
        verify(jdbc).update(startsWith("UPDATE alarm_event_log"), eq(1001L), eq(1L));
        verify(jdbc).update(startsWith("UPDATE device SET device_id"), eq(1001L), eq(1L));
        verify(jdbc).update(startsWith("UPDATE device SET building"), eq(1), eq(1), eq(101), eq(1001L));
    }

    /**
     * 旧 DEMO 503 完整迁移为 1083：
     * 同步迁移直接引用 device_id 的历史数据。
     */
    @Test
    void demo503MigratesTo1083() {

        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        DatabaseInitializer initializer =
                new DatabaseInitializer(jdbc);

        // 旧ID 1 不存在；503 存在；1083 不存在；101 不存在
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(1L)))
                .thenReturn(0L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(503L)))
                .thenReturn(1L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(1083L)))
                .thenReturn(0L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(101L)))
                .thenReturn(0L);

        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("smoke_record")))
                .thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("alarm")))
                .thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("alarm_event_log")))
                .thenReturn(1);

        when(jdbc.update(startsWith("UPDATE smoke_record"), any(), any()))
                .thenReturn(1);
        when(jdbc.update(startsWith("UPDATE alarm SET"), any(), any()))
                .thenReturn(1);
        when(jdbc.update(startsWith("UPDATE alarm_event_log"), any(), any()))
                .thenReturn(1);
        when(jdbc.update(startsWith("UPDATE device SET device_id"), any(), any()))
                .thenReturn(1);
        when(jdbc.update(startsWith("UPDATE device SET building"), any(), any(), any(), any()))
                .thenReturn(1);

        ReflectionTestUtils.invokeMethod(
                initializer,
                "migrateLegacyDeviceIds"
        );

        verify(jdbc).update(startsWith("UPDATE smoke_record"), eq(1083L), eq(503L));
        verify(jdbc).update(startsWith("UPDATE device SET device_id"), eq(1083L), eq(503L));
    }

    /**
     * 重复启动幂等：
     * 所有旧ID（1/503/101）都已不存在时，不产生任何迁移动作。
     */
    @Test
    void migrationIsIdempotentOnSecondRun() {

        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        DatabaseInitializer initializer =
                new DatabaseInitializer(jdbc);

        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(1L)))
                .thenReturn(0L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(503L)))
                .thenReturn(0L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(101L)))
                .thenReturn(0L);

        when(jdbc.update(startsWith("UPDATE device SET building"), any(), any(), any(), any()))
                .thenReturn(1);

        ReflectionTestUtils.invokeMethod(
                initializer,
                "migrateLegacyDeviceIds"
        );

        verify(jdbc, never()).update(startsWith("UPDATE smoke_record"), any(), any());
        verify(jdbc, never()).update(startsWith("UPDATE alarm SET"), any(), any());
        verify(jdbc, never()).update(startsWith("UPDATE device SET device_id"), any(), any());
        verify(jdbc).update(startsWith("UPDATE device SET building"), eq(1), eq(1), eq(101), eq(1001L));
    }

    /**
     * 目标ID已存在冲突 DEMO 时：
     * 先安全清理该 DEMO 及其测试历史，再迁移真实设备，
     * 绝不删除真实硬件数据。
     */
    @Test
    void conflictingDemoAtTargetCleanedBeforeRealMigration() {

        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        DatabaseInitializer initializer =
                new DatabaseInitializer(jdbc);

        // 旧ID 1 与 1001 都存在；503 / 101 不存在
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(1L)))
                .thenReturn(1L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(1001L)))
                .thenReturn(1L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(503L)))
                .thenReturn(0L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(101L)))
                .thenReturn(0L);

        // 1001 是 DEMO（可安全清理，非真实硬件）
        when(jdbc.queryForList(anyString(), eq(String.class), eq(1001L)))
                .thenReturn(List.of("DEMO"));

        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("alarm_event_log")))
                .thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("smoke_record")))
                .thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("alarm")))
                .thenReturn(1);

        // 清理 1001 的历史
        when(jdbc.update(startsWith("DELETE FROM alarm_event_log"), eq(1001L)))
                .thenReturn(1);
        when(jdbc.update(startsWith("DELETE FROM smoke_record"), eq(1001L)))
                .thenReturn(1);
        when(jdbc.update(startsWith("DELETE FROM alarm WHERE"), eq(1001L)))
                .thenReturn(1);
        when(jdbc.update(startsWith("DELETE FROM device"), eq(1001L)))
                .thenReturn(1);

        when(jdbc.update(startsWith("UPDATE smoke_record"), any(), any()))
                .thenReturn(1);
        when(jdbc.update(startsWith("UPDATE alarm SET"), any(), any()))
                .thenReturn(1);
        when(jdbc.update(startsWith("UPDATE alarm_event_log"), any(), any()))
                .thenReturn(1);
        when(jdbc.update(startsWith("UPDATE device SET device_id"), any(), any()))
                .thenReturn(1);
        when(jdbc.update(startsWith("UPDATE device SET building"), any(), any(), any(), any()))
                .thenReturn(1);

        ReflectionTestUtils.invokeMethod(
                initializer,
                "migrateLegacyDeviceIds"
        );

        verify(jdbc).update(startsWith("DELETE FROM device"), eq(1001L));
        verify(jdbc).update(startsWith("UPDATE smoke_record"), eq(1001L), eq(1L));
        verify(jdbc).update(startsWith("UPDATE device SET device_id"), eq(1001L), eq(1L));
    }
}
