package com.example.demo.config;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ForestDatabaseInitializer 森林监测节点规划纯函数测试。
 *
 * 覆盖：
 * - 设备ID → 森林分区映射（1001~1024→Z01 ... 1069~1090→Z04）；
 * - 节点编号推导（1001→FS-N-001 ... 1090→FS-N-090）；
 * - 分区名称映射；
 * - 90 个监测节点的地图坐标全部落在 1000×650 视口内、编号唯一。
 */
public class ForestDatabaseInitializerTest {

    @Test
    void zoneMappingFollowsDeviceRanges() {

        assertEquals(1, ForestDatabaseInitializer.zoneIdForDevice(1001),
                "1001（真实硬件）属于 Z01");
        assertEquals(1, ForestDatabaseInitializer.zoneIdForDevice(1024));
        assertEquals(2, ForestDatabaseInitializer.zoneIdForDevice(1025),
                "1025 起进入 Z02");
        assertEquals(2, ForestDatabaseInitializer.zoneIdForDevice(1046));
        assertEquals(3, ForestDatabaseInitializer.zoneIdForDevice(1047));
        assertEquals(3, ForestDatabaseInitializer.zoneIdForDevice(1068));
        assertEquals(4, ForestDatabaseInitializer.zoneIdForDevice(1069),
                "1069 起进入 Z04");
        assertEquals(4, ForestDatabaseInitializer.zoneIdForDevice(1090));
    }

    @Test
    void zoneMappingRejectsOutOfRangeDevices() {

        assertThrows(IllegalArgumentException.class,
                () -> ForestDatabaseInitializer.zoneIdForDevice(1000));
        assertThrows(IllegalArgumentException.class,
                () -> ForestDatabaseInitializer.zoneIdForDevice(1091));
    }

    @Test
    void nodeCodeDerivationIsStable() {

        assertEquals("FS-N-001",
                ForestDatabaseInitializer.nodeCodeForDevice(1001));
        assertEquals("FS-N-002",
                ForestDatabaseInitializer.nodeCodeForDevice(1002));
        assertEquals("FS-N-026",
                ForestDatabaseInitializer.nodeCodeForDevice(1026));
        assertEquals("FS-N-090",
                ForestDatabaseInitializer.nodeCodeForDevice(1090));
    }

    @Test
    void zoneNamesAreMapped() {

        assertEquals("北部核心保护区",
                ForestDatabaseInitializer.zoneNameFor(1));
        assertEquals("东部防火通道区",
                ForestDatabaseInitializer.zoneNameFor(2));
        assertEquals("西部生态保育区",
                ForestDatabaseInitializer.zoneNameFor(3));
        assertEquals("南部生态涵养区",
                ForestDatabaseInitializer.zoneNameFor(4));
    }

    /**
     * 90 个监测节点规划：
     * - 节点编号全部唯一；
     * - 地图坐标全部落在 SVG 视口 0~1000 × 0~650 内；
     * - 地理坐标落在合理范围（纬度 28.0~29.0，经度 113.0~114.0）。
     */
    @Test
    void allNodePlansAreInBoundsAndUnique() {

        Set<String> codes = new HashSet<>();

        for (long id = 1001L; id <= 1090L; id++) {

            ForestDatabaseInitializer.NodePlan plan =
                    ForestDatabaseInitializer.planNode(id);

            assertTrue(codes.add(plan.nodeCode()),
                    "节点编号必须唯一，重复：" + plan.nodeCode());

            assertTrue(plan.mapX() >= 0 && plan.mapX() <= 1000,
                    "mapX 越界：" + plan);
            assertTrue(plan.mapY() >= 0 && plan.mapY() <= 650,
                    "mapY 越界：" + plan);

            double lat = plan.latitude().doubleValue();
            double lng = plan.longitude().doubleValue();

            assertTrue(lat >= 28.0 && lat <= 29.0,
                    "纬度越界：" + plan);
            assertTrue(lng >= 113.0 && lng <= 114.0,
                    "经度越界：" + plan);

            assertEquals(
                    ForestDatabaseInitializer.zoneIdForDevice(id),
                    plan.zoneId(),
                    "plan 的分区必须与 zoneIdForDevice 一致");
        }

        assertEquals(90, codes.size());
    }
}
