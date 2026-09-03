package com.example.demo.ai.service;

import com.example.demo.ai.support.AiAuthService.Principal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ForestAiToolServiceRulesTest {

    @Test
    void planDetectsActiveIncidentsAndToday() {
        List<Map<String, Object>> calls = ForestAiToolService.planTools("今天有多少未结案的火情？");
        assertTrue(calls.stream().anyMatch(c -> "queryActiveIncidents".equals(c.get("tool"))));
    }

    @Test
    void planDetectsIncidentIdForDetail() {
        List<Map<String, Object>> calls = ForestAiToolService.planTools("看一下事件 DEMO-FIRE-1725000000000 的详情");
        assertTrue(calls.stream().anyMatch(c -> "queryIncidentDetail".equals(c.get("tool"))));
    }

    @Test
    void planDetectsSensorHistoryWithHours() {
        List<Map<String, Object>> calls = ForestAiToolService.planTools("GT-01 最近6小时传感器数据趋势如何？");
        Map<String, Object> history = calls.stream()
                .filter(c -> "querySensorHistory".equals(c.get("tool"))).findFirst().orElse(null);
        assertNotNull(history);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) history.get("params");
        assertEquals(6, params.get("hours"));
        assertEquals("GT-01", params.get("deviceId"));
    }

    @Test
    void planDetectsHighRiskPatrolAndWeather() {
        assertTrue(ForestAiToolService.planTools("分析当前高风险设备").stream()
                .anyMatch(c -> "queryHighRiskDevices".equals(c.get("tool"))));
        assertTrue(ForestAiToolService.planTools("生成今日巡护总结").stream()
                .anyMatch(c -> "generatePatrolSummary".equals(c.get("tool"))));
        assertTrue(ForestAiToolService.planTools("现在园区天气和风险等级怎么样").stream()
                .anyMatch(c -> "queryWeatherSummary".equals(c.get("tool"))));
    }

    @Test
    void hoursAndLimitsAreClamped() {
        assertEquals(24, ForestAiToolService.extractHours("最近的数据怎么样"));
        assertEquals(5, ForestAiToolService.extractHours("最近5小时"));
        assertEquals(72, ForestAiToolService.clampHours(999));
        assertEquals(1, ForestAiToolService.clampHours(0));
        assertEquals(200, ForestAiToolService.clampLimit(5000, 60));
        assertEquals(60, ForestAiToolService.clampLimit(null, 60));
    }

    @Test
    void illegalIdsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ForestAiToolService.requireId("GT-01; DROP TABLE", "设备编号"));
        assertThrows(IllegalArgumentException.class,
                () -> ForestAiToolService.requireId("../../etc/passwd", "设备编号"));
        assertThrows(IllegalArgumentException.class,
                () -> ForestAiToolService.requireId("", "设备编号"));
        assertEquals("GT-01", ForestAiToolService.requireId("GT-01", "设备编号"));
    }

    @Test
    void unknownToolIsRefusedAndAnonymousIsBlocked() {
        Principal ranger = new Principal(1L, "ranger", "ranger");
        ForestAiToolService service = new ForestAiToolService(null, null, null, null);
        ForestAiToolService.ToolResult unknown = service.invoke("DROP_TABLE", null, ranger);
        assertFalse(unknown.ok);
        ForestAiToolService.ToolResult anon = service.invoke("queryActiveIncidents", null, null);
        assertFalse(anon.ok);
    }

    @Test
    void toolListMatchesWhitelist() {
        assertEquals(10, ForestAiToolService.TOOL_NAMES.size());
        assertTrue(ForestAiToolService.TOOL_NAMES.contains("querySensorHistory"));
        assertFalse(ForestAiToolService.TOOL_NAMES.stream().anyMatch(n -> n.toLowerCase().contains("delete")));
        assertFalse(ForestAiToolService.TOOL_NAMES.stream().anyMatch(n -> n.toLowerCase().contains("update")));
    }

    @Test
    void knowledgeAllowlistBlocksSecretsAndBuildDirs() {
        assertTrue(KnowledgeIngestionService.isAllowedPath("01-系统与模块总览.md"));
        assertTrue(KnowledgeIngestionService.isAllowedPath("uploads/ab12.pdf"));
        assertTrue(KnowledgeIngestionService.isAllowedPath("docs/README"));
        assertFalse(KnowledgeIngestionService.isAllowedPath(".env.local"));
        assertFalse(KnowledgeIngestionService.isAllowedPath("deploy/.env.example"));
        assertFalse(KnowledgeIngestionService.isAllowedPath("application-prod.yml"));
        assertFalse(KnowledgeIngestionService.isAllowedPath("node_modules/x.md"));
        assertFalse(KnowledgeIngestionService.isAllowedPath("demo/target/app.md"));
        assertFalse(KnowledgeIngestionService.isAllowedPath("logs/app.log"));
        assertFalse(KnowledgeIngestionService.isAllowedPath("db_password.txt"));
        assertFalse(KnowledgeIngestionService.isAllowedPath("notes.md.exe"));
        assertFalse(KnowledgeIngestionService.isAllowedPath("LICENSE"));
    }

    @Test
    void auditParamsAreRedacted() {
        java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("username", "ranger");
        params.put("password", "ranger123");
        params.put("Authorization", "Bearer mock-token-ranger");
        params.put("api_key", "sk-cloud-secret");
        params.put("question", "今天火情如何");
        String json = AiAuditService.sanitizeParameters(params);
        assertTrue(json.contains("\"[redacted]\""));
        assertFalse(json.contains("ranger123"));
        assertFalse(json.contains("mock-token"));
        assertFalse(json.contains("sk-cloud-secret"));
        assertTrue(json.contains("今天火情如何"));
    }
}
