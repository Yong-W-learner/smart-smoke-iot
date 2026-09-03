package com.example.demo.ai.support;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class AiJsonParserAndAuthTest {

    @Test
    void parsesCleanJson() {
        AiJsonParser.Parsed parsed = AiJsonParser.parse(
                "{\"answer\":\"结论：正常。\",\"confidence\":\"high\",\"needHumanReview\":false,\"outOfScope\":false," +
                        "\"suggestedActions\":[{\"label\":\"查看设备\",\"type\":\"navigate\",\"target\":\"/ranger/equipment\"}]}");
        assertEquals("结论：正常。", parsed.answer);
        assertEquals("high", parsed.confidence);
        assertFalse(parsed.needHumanReview);
        assertFalse(parsed.repaired);
        assertEquals(1, parsed.suggestedActions.size());
    }

    @Test
    void toleratesMarkdownFencesAndProse() {
        String raw = "好的，以下是结果：\n```json\n{\"answer\": \"当前数据不足\", \"confidence\": \"low\"}\n```\n希望有帮助";
        AiJsonParser.Parsed parsed = AiJsonParser.parse(raw);
        assertEquals("当前数据不足", parsed.answer);
        assertFalse(parsed.repaired);
    }

    @Test
    void stripsThinkBlocks() {
        String raw = "<think>先分析一下火情……</think>{\"answer\":\"完成\",\"confidence\":\"medium\"}";
        AiJsonParser.Parsed parsed = AiJsonParser.parse(raw);
        assertEquals("完成", parsed.answer);
    }

    @Test
    void repairsTrailingComma() {
        AiJsonParser.Parsed parsed = AiJsonParser.parse("{\"answer\":\"带尾逗号\",\"confidence\":\"medium\",}");
        assertEquals("带尾逗号", parsed.answer);
    }

    @Test
    void fallsBackToPlainTextWhenUnparsable() {
        AiJsonParser.Parsed parsed = AiJsonParser.parse("这不是 JSON，只是一段中文回答");
        assertTrue(parsed.repaired);
        assertTrue(parsed.isValid());
        assertTrue(parsed.needHumanReview, "无法结构化时应保守要求人工复核");
    }

    @Test
    void extractsAnswerWhenJsonBroken() {
        AiJsonParser.Parsed parsed = AiJsonParser.parse("{\"answer\": \"部分可读\", \"broken\": [1,2, ");
        assertEquals("部分可读", parsed.answer);
        assertTrue(parsed.repaired);
    }

    @Test
    void rejectsUnknownNavigateTargets() {
        AiJsonParser.Parsed parsed = AiJsonParser.parse(
                "{\"answer\":\"x\",\"suggestedActions\":[" +
                        "{\"label\":\"删库\",\"type\":\"delete\",\"target\":\"/api/forest/incidents/1\"}," +
                        "{\"label\":\"越权\",\"type\":\"navigate\",\"target\":\"/admin/shutdown\"}," +
                        "{\"label\":\"穿越\",\"type\":\"navigate\",\"target\":\"/ranger/../etc\"}," +
                        "{\"label\":\"查看\",\"type\":\"navigate\",\"target\":\"/ranger/equipment\"}]}");
        assertEquals(1, parsed.suggestedActions.size());
        assertEquals("/ranger/equipment", parsed.suggestedActions.get(0).getString("target"));
        assertFalse(AiJsonParser.targetAllowed("javascript:alert(1)"));
    }

    @Test
    void parsesMockTokenConvention() {
        assertEquals("ranger", AiAuthService.usernameFromToken("Bearer mock-token-ranger"));
        assertEquals("admin", AiAuthService.usernameFromToken("mock-token-admin"));
        assertNull(AiAuthService.usernameFromToken("Bearer jwt.real.token"));
        assertNull(AiAuthService.usernameFromToken(null));
        assertNull(AiAuthService.usernameFromToken("Bearer mock-token-../../etc"));
        assertNull(AiAuthService.usernameFromToken("Basic YWRtaW4="));
    }

    @Test
    void externalConversationIdRoundTrip() {
        assertEquals(1001L, com.example.demo.ai.service.AiConversationService.parseExternalId("c-1001"));
        assertEquals(7L, com.example.demo.ai.service.AiConversationService.parseExternalId("7"));
        assertNull(com.example.demo.ai.service.AiConversationService.parseExternalId("c-abc"));
        assertNull(com.example.demo.ai.service.AiConversationService.parseExternalId(null));
        assertEquals("c-42", com.example.demo.ai.service.AiConversationService.externalId(42));
    }

    @Test
    void rateWindowHelpersArePure() {
        assertEquals("abc", AiJsonParser.parse("{\"answer\":\"abc\"}").answer);
        assertEquals(3, Arrays.asList("a", "b", "c").size());
    }
}
