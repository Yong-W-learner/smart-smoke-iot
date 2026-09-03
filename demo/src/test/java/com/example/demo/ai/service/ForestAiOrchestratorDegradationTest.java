package com.example.demo.ai.service;

import com.example.demo.ai.client.OllamaClient;
import com.example.demo.ai.client.QdrantClient;
import com.example.demo.ai.config.ForestAiProperties;
import com.example.demo.ai.support.AiAuthService.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AI 关闭或本地模型不可用时的降级行为：不抛异常、给出可读中文、不阻断业务。
 */
class ForestAiOrchestratorDegradationTest {

    private ForestAiProperties properties;
    private OllamaClient ollamaClient;
    private QdrantClient qdrantClient;
    private KnowledgeRetrievalService retrievalService;
    private KnowledgeIngestionService ingestionService;
    private ForestAiToolService toolService;
    private AiConversationService conversationService;
    private AiAuditService auditService;
    private ForestAiOrchestratorService orchestrator;
    private final Principal ranger = new Principal(1L, "ranger", "ranger");

    @BeforeEach
    void setUp() {
        properties = new ForestAiProperties();
        ollamaClient = mock(OllamaClient.class);
        qdrantClient = mock(QdrantClient.class);
        retrievalService = mock(KnowledgeRetrievalService.class);
        ingestionService = mock(KnowledgeIngestionService.class);
        toolService = mock(ForestAiToolService.class);
        conversationService = mock(AiConversationService.class);
        auditService = mock(AiAuditService.class);
        orchestrator = new ForestAiOrchestratorService(properties, ollamaClient, qdrantClient, retrievalService,
                ingestionService, toolService, conversationService, auditService, mock(JdbcTemplate.class));
        when(conversationService.ensureConversation(any(), any(), any())).thenReturn(11L);
        when(conversationService.recentMessages(anyLong(), anyInt())).thenReturn(Collections.emptyList());
        when(ingestionService.statusSummary()).thenReturn(Collections.emptyMap());
        when(toolService.invoke(anyString(), any(), any())).thenAnswer(invocation -> {
            ForestAiToolService.ToolResult result = new ForestAiToolService.ToolResult(invocation.getArgument(0));
            result.ok = false;
            result.error = "测试环境不执行真实查询";
            return result;
        });
    }

    @Test
    void disabledAiReturnsReadableDegradedAnswerWithoutTouchingModel() {
        properties.setEnabled(false);
        Map<String, Object> response = orchestrator.chat(ranger, null, "今天火情如何");
        assertEquals(Boolean.TRUE, response.get("degraded"));
        assertEquals("disabled", response.get("model"));
        String answer = (String) response.get("answer");
        assertTrue(answer.contains("未启用"));
        verifyNoInteractions(ollamaClient);
        verify(conversationService, never()).ensureConversation(any(), any(), any());
    }

    @Test
    void disabledHealthStillAnswersWithoutThrowing() {
        properties.setEnabled(false);
        Map<String, Object> health = orchestrator.health();
        assertEquals(Boolean.FALSE, health.get("aiEnabled"));
        assertEquals(Boolean.TRUE, health.get("degraded"));
        verifyNoInteractions(ollamaClient);
    }

    @Test
    void modelOutageFallsBackToRealtimeDataDigest() {
        when(ollamaClient.chat(anyString(), any(), any(), anyBoolean()))
                .thenThrow(new OllamaClient.OllamaException("无法访问本地 AI 服务"));
        KnowledgeRetrievalService.RetrievalResult retrieval = new KnowledgeRetrievalService.RetrievalResult();
        retrieval.degraded = true;
        retrieval.degradedReason = "本地向量库不可用";
        when(retrievalService.retrieve(anyString(), any())).thenReturn(retrieval);
        when(ingestionService.statusSummary()).thenReturn(Collections.emptyMap());

        Map<String, Object> response = orchestrator.chat(ranger, null, "现在系统里有未处理火情吗");

        assertEquals(Boolean.TRUE, response.get("degraded"));
        assertEquals("unavailable", response.get("model"));
        String answer = (String) response.get("answer");
        assertNotNull(answer);
        assertTrue(answer.contains("不可用"));
        assertTrue(answer.contains("人工复核"));
        verify(conversationService).appendMessage(eq(11L), eq("assistant"), anyString(), any(), eq("unavailable"), anyLong());
    }

    @Test
    void emptyInputIsRejectedWithoutModelCall() {
        Map<String, Object> response = orchestrator.chat(ranger, null, "   ");
        assertEquals(Boolean.TRUE, response.get("degraded"));
        assertTrue(((String) response.get("answer")).contains("请输入问题"));
        verifyNoInteractions(ollamaClient);
    }

    @Test
    void offTopicQuestionGetsScopedRefusal() {
        when(ingestionService.statusSummary()).thenReturn(Collections.emptyMap());
        Map<String, Object> response = orchestrator.chat(ranger, null, "帮我写一篇关于春天的诗歌");
        assertTrue(((String) response.get("answer")).contains("只能回答本森林防火项目相关问题"));
        verifyNoInteractions(ollamaClient);
        verify(conversationService).appendMessage(eq(11L), eq("assistant"), anyString(), any(), eq("off-topic"), anyLong());
    }

    @Test
    void modelJsonAnswerIsStructuredToResponse() {
        when(ollamaClient.chat(anyString(), any(), any(), anyBoolean()))
                .thenReturn("{\"answer\":\"结论：当前没有未结案火情。\",\"confidence\":\"high\",\"needHumanReview\":false," +
                        "\"outOfScope\":false,\"suggestedActions\":[{\"label\":\"查看工作台\",\"type\":\"navigate\",\"target\":\"/ranger\"}]}");
        when(retrievalService.retrieve(anyString(), any())).thenReturn(new KnowledgeRetrievalService.RetrievalResult());
        Map<String, Object> response = orchestrator.chat(ranger, null, "当前有没有活跃火情");
        assertEquals("c-11", response.get("conversationId"));
        assertTrue(((String) response.get("answer")).contains("没有未结案火情"));
        assertEquals("qwen3:4b", response.get("model"));
        assertFalse((Boolean) response.get("needHumanReview"));
        assertEquals(1, ((java.util.List<?>) response.get("suggestedActions")).size());
    }
}
