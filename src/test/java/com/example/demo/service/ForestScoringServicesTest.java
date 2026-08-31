package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.demo.entity.AncientTree;
import com.example.demo.entity.EnvironmentRecord;
import com.example.demo.entity.WildlifeHabitat;
import com.example.demo.mapper.AncientTreeMapper;
import com.example.demo.mapper.EnvironmentRecordMapper;
import com.example.demo.mapper.WildlifeHabitatMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 森林火险评分服务测试。
 *
 * 覆盖：
 * - 火险可信度公式（烟雾证据分×0.7 + 云端/边缘/无人机加分，封顶100）；
 * - 火险气象评分（温度/湿度/土壤湿度加权，无数据返回中性默认值）；
 * - 事件优先级公式（火险×0.4 + 气象×0.25 + 生态影响，
 *   无人机确认强制 ≥95）；
 * - 优先级等级阈值与 Haversine 球面距离。
 */
public class ForestScoringServicesTest {

    /* ==================================================
       火险可信度 ForestFireRiskService
       ================================================== */

    @Test
    void fireConfidenceCombinesSmokeCloudEdgeAndDrone() {

        ForestFireRiskService service = new ForestFireRiskService();

        // 烟雾证据分 100 × 0.7 + 云端ALARM 15 + 边缘ALARM 5 = 90
        assertEquals(90, service.fireConfidenceScore(
                100.0, "ALARM", "ALARM", false));

        // 证据分 50 × 0.7 + 云端WARNING 0 + 边缘PREWARNING 0 = 35
        assertEquals(35, service.fireConfidenceScore(
                50.0, "WARNING", "PREWARNING", false));

        // 无人机确认 +20：80 × 0.7 + 15 + 5 + 20 = 96
        assertEquals(96, service.fireConfidenceScore(
                80.0, "ALARM", "ALARM", true));
    }

    @Test
    void fireConfidenceIsCappedAt100() {

        ForestFireRiskService service = new ForestFireRiskService();

        assertEquals(100, service.fireConfidenceScore(
                100.0, "ALARM", "ALARM", true),
                "总分封顶 100");
    }

    @Test
    void fireConfidenceHandlesNullSmokeScore() {

        ForestFireRiskService service = new ForestFireRiskService();

        assertEquals(15, service.fireConfidenceScore(
                null, "ALARM", null, false),
                "证据分缺失时按 0 处理，仅保留云端 ALARM 加分");
    }

    /* ==================================================
       火险气象评分 FireWeatherService
       ================================================== */

    @Test
    void weatherScoreWeightsTempHumiditySoil() {

        FireWeatherService service =
                new FireWeatherService(envMapper(30, 40, 30));

        int score = service.score(1L);

        // temp(30→65) ×0.30 + humidity(40→66.67) ×0.30
        //      + soil(30→71.25) ×0.40 = 19.5 + 20.0 + 28.5 = 68
        assertTrue(score >= 60 && score <= 75,
                "高温低湿低土壤湿度应得到较高气象评分：" + score);
    }

    @Test
    void weatherScoreUsesFrozenWeights() {

        // 冻结权重：温度 0.30 / 湿度 0.30 / 土壤湿度 0.40，
        // 三项贡献 0~30 + 0~30 + 0~40 = 0~100，禁止再改动。
        FireWeatherService dryHot =
                new FireWeatherService(envMapper(40, 20, 20));

        // temp(40→95)×0.30 + humidity(20→95)×0.30 + soil(20→90)×0.40
        // = 28.5 + 28.5 + 36 = 93
        assertEquals(93, dryHot.score(1L),
                "高温低湿低土壤湿度按冻结权重应为 93");

        FireWeatherService wetCool =
                new FireWeatherService(envMapper(15, 80, 60));

        // temp(15→20)×0.30 + humidity(80→10)×0.30 + soil(60→15)×0.40
        // = 6 + 3 + 6 = 15
        assertEquals(15, wetCool.score(1L),
                "低温高湿高土壤湿度按冻结权重应为 15");
    }

    @Test
    void weatherScoreFallsBackToNeutralWhenNoData() {

        FireWeatherService service =
                new FireWeatherService(mock(EnvironmentRecordMapper.class));

        assertEquals(FireWeatherService.DEFAULT_WEATHER_SCORE,
                service.score(null),
                "无分区或无数据时返回中性默认值");
    }

    @Test
    void humidityInverseRuleLowHumidityHighScore() {

        assertEquals(95.0, FireWeatherService.humidityScore(
                new BigDecimal("20")), 0.001);
        assertEquals(10.0, FireWeatherService.humidityScore(
                new BigDecimal("80")), 0.001);
    }

    @Test
    void temperatureHighScoreRule() {

        assertEquals(20.0, FireWeatherService.temperatureScore(
                new BigDecimal("15")), 0.001);
        assertEquals(95.0, FireWeatherService.temperatureScore(
                new BigDecimal("40")), 0.001);
    }

    /* ==================================================
       事件优先级 ForestEventPriorityService
       ================================================== */

    @Test
    void priorityScoreFormulaWithImpacts() {

        ForestEventPriorityService service = newPriorityService();

        // 90×0.40 + 60×0.25 + 10 + 10 = 36 + 15 + 20 = 71
        assertEquals(71, service.priorityScore(
                90, 60, 10, 10, false));
    }

    @Test
    void droneConfirmedForcesMinimum95() {

        ForestEventPriorityService service = newPriorityService();

        assertEquals(95, service.priorityScore(
                40, 30, 0, 0, true),
                "无人机确认火点后优先级强制 ≥95");
        assertEquals(96, service.priorityScore(
                90, 80, 20, 20, true),
                "无人机确认后不压低已较高分（90×0.4+80×0.25+20+20=96）");
    }

    @Test
    void priorityScoreCappedAt100() {

        ForestEventPriorityService service = newPriorityService();

        assertEquals(100, service.priorityScore(
                100, 100, 100, 100, false));
    }

    @Test
    void priorityLevelThresholds() {

        ForestEventPriorityService service = newPriorityService();

        assertEquals("RED", service.priorityLevel(80));
        assertEquals("RED", service.priorityLevel(100));
        assertEquals("ORANGE", service.priorityLevel(60));
        assertEquals("YELLOW", service.priorityLevel(40));
        assertEquals("LOW", service.priorityLevel(39));
    }

    @Test
    void haversineDistanceMatchesKnownValue() {

        // 纬度每 1° 约 111.19 公里
        double d = ForestEventPriorityService.haversineMeters(
                28.0, 113.0, 29.0, 113.0);

        assertEquals(111194.0, d, 500);
    }

    @Test
    void treeImpactBandFollowsFrozenTiers() {

        // ≤100m：一级20 / 二级15 / 三级10
        assertEquals(20, ForestEventPriorityService.treeImpactBand(
                86, "一级"));
        assertEquals(15, ForestEventPriorityService.treeImpactBand(
                50, "二级"));
        assertEquals(10, ForestEventPriorityService.treeImpactBand(
                10, "三级"));

        // 100~250m：一级12 / 二级8 / 三级5
        assertEquals(12, ForestEventPriorityService.treeImpactBand(
                180, "一级"));
        assertEquals(8, ForestEventPriorityService.treeImpactBand(
                150, "二级"));
        assertEquals(5, ForestEventPriorityService.treeImpactBand(
                249, "三级"));

        // >250m 记 0
        assertEquals(0, ForestEventPriorityService.treeImpactBand(
                251, "一级"));
    }

    @Test
    void habitatImpactBandFollowsFrozenTiers() {

        // ≤200m：CORE15 / HIGH10 / MEDIUM5
        assertEquals(15, ForestEventPriorityService.habitatImpactBand(
                86, "CORE"));
        assertEquals(10, ForestEventPriorityService.habitatImpactBand(
                142, "HIGH"));
        assertEquals(5, ForestEventPriorityService.habitatImpactBand(
                200, "MEDIUM"));

        // >200m 记 0
        assertEquals(0, ForestEventPriorityService.habitatImpactBand(
                201, "CORE"));
    }

    @Test
    void impactScoresReturnTopTierAndAffectedResources() {

        AncientTreeMapper treeMapper = mock(AncientTreeMapper.class);
        WildlifeHabitatMapper habitatMapper =
                mock(WildlifeHabitatMapper.class);

        AncientTree nearTree = new AncientTree();
        nearTree.setTreeCode("AT-001");
        nearTree.setTreeName("北部古松");
        nearTree.setProtectionLevel("一级");
        nearTree.setLatitude(new BigDecimal("28.1800000"));
        nearTree.setLongitude(new BigDecimal("113.1000000"));

        AncientTree farTree = new AncientTree();
        farTree.setTreeCode("AT-002");
        farTree.setProtectionLevel("一级");
        farTree.setLatitude(new BigDecimal("28.2200000"));
        farTree.setLongitude(new BigDecimal("113.1000000"));

        when(treeMapper.selectList(any())).thenReturn(
                List.of(nearTree, farTree));

        WildlifeHabitat coreHabitat = new WildlifeHabitat();
        coreHabitat.setHabitatCode("WH-001");
        coreHabitat.setHabitatName("北部林冠鸟类栖息地");
        coreHabitat.setProtectionLevel("CORE");
        coreHabitat.setLatitude(new BigDecimal("28.1790000"));
        coreHabitat.setLongitude(new BigDecimal("113.0990000"));

        WildlifeHabitat farHabitat = new WildlifeHabitat();
        farHabitat.setHabitatCode("WH-004");
        farHabitat.setProtectionLevel("MEDIUM");
        farHabitat.setLatitude(new BigDecimal("28.2300000"));
        farHabitat.setLongitude(new BigDecimal("113.1000000"));

        when(habitatMapper.selectList(any())).thenReturn(
                List.of(coreHabitat, farHabitat));

        ForestEventPriorityService service =
                new ForestEventPriorityService(treeMapper, habitatMapper);

        // 事件点紧邻一级古树（≤100m → 20），CORE 栖息地约 146m（≤200m → 15）；
        // 远距离资源 >250m / >200m 记 0，不参与影响分
        ForestEventPriorityService.ImpactScores scores =
                service.impactScores(1L,
                        new BigDecimal("28.1800000"),
                        new BigDecimal("113.1000000"));

        assertEquals(20, scores.ancientTreeImpactScore(),
                "最近一级古树 ≤100m → 20");
        assertEquals(15, scores.wildlifeImpactScore(),
                "CORE 栖息地 ≤200m → 15");

        List<ForestEventPriorityService.ResourceImpact> affected =
                scores.affectedResources();

        assertEquals(2, affected.size(),
                "仅命中分档的资源进入受影响列表：" + affected);
        assertEquals("AT-001", affected.get(0).code());
        assertEquals(ForestEventPriorityService.RESOURCE_TYPE_TREE,
                affected.get(0).type());
        assertEquals("WH-001", affected.get(1).code());
        assertEquals(ForestEventPriorityService.RESOURCE_TYPE_HABITAT,
                affected.get(1).type());
    }

    @Test
    void impactScoresUseRealDistanceAcrossZones() {

        AncientTreeMapper treeMapper = mock(AncientTreeMapper.class);
        WildlifeHabitatMapper habitatMapper =
                mock(WildlifeHabitatMapper.class);

        // Z01 的一级古树距离事件点很近，但事件分区是 Z02
        AncientTree z1Tree = new AncientTree();
        z1Tree.setTreeCode("AT-001");
        z1Tree.setProtectionLevel("一级");
        z1Tree.setZoneId(1L);
        z1Tree.setLatitude(new BigDecimal("28.1800000"));
        z1Tree.setLongitude(new BigDecimal("113.1000000"));

        when(treeMapper.selectList(any())).thenReturn(List.of(z1Tree));
        when(habitatMapper.selectList(any())).thenReturn(List.of());

        ForestEventPriorityService service =
                new ForestEventPriorityService(treeMapper, habitatMapper);

        // 事件点在 Z02，但与 Z01 古树距离 ~0m → 按真实距离参与计分，
        // 不得做分区过滤（邻近分区资源同样受威胁）
        ForestEventPriorityService.ImpactScores scores =
                service.impactScores(2L,
                        new BigDecimal("28.1800000"),
                        new BigDecimal("113.1000000"));

        assertEquals(20, scores.ancientTreeImpactScore(),
                "跨分区古树按真实距离参与计分");
    }

    @Test
    void impactScoresReturnZeroWhenNoResources() {

        ForestEventPriorityService service = newPriorityService();

        ForestEventPriorityService.ImpactScores scores =
                service.impactScores(9L,
                        new BigDecimal("28.1800000"),
                        new BigDecimal("113.1000000"));

        assertEquals(0, scores.ancientTreeImpactScore());
        assertEquals(0, scores.wildlifeImpactScore());
    }

    @Test
    void priorityReasonIsTransparentRuleText() {

        ForestEventPriorityService service = newPriorityService();

        String reason = service.priorityReason(
                80, 60, 20, 10, 80, false);

        assertTrue(reason.contains("×0.40"));
        assertTrue(reason.contains("×0.25"));
        assertTrue(reason.contains("优先级80"));
        assertTrue(reason.contains("RED"));
        assertTrue(reason.contains("无人机复核未执行"));
    }

    /* ==================================================
       测试工具
       ================================================== */

    private static ForestEventPriorityService newPriorityService() {

        return new ForestEventPriorityService(
                mock(AncientTreeMapper.class),
                mock(WildlifeHabitatMapper.class));
    }

    private static EnvironmentRecordMapper envMapper(
            double temp, double humidity, double soil) {

        EnvironmentRecord record = new EnvironmentRecord();
        record.setZoneId(1L);
        record.setTemperature(new BigDecimal(temp));
        record.setHumidity(new BigDecimal(humidity));
        record.setSoilMoisture(new BigDecimal(soil));

        EnvironmentRecordMapper mapper =
                mock(EnvironmentRecordMapper.class);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(record);

        return mapper;
    }
}
