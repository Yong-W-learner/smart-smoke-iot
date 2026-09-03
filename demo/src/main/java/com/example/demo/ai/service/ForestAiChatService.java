package com.example.demo.ai.service;

import com.example.demo.ai.config.ForestAiProperties;
import com.example.demo.ai.support.AiAuthService.Principal;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * AI 助手对控制器的门面：对话、会话管理、健康检查与知识库管理入口。
 * 权限在这里统一裁决：普通护林员只能读写自己的会话；知识库导入/重建仅 admin。
 */
@Service
public class ForestAiChatService {

    /** 携带业务码的异常：401 未登录 / 403 无权限 / 404 不存在。 */
    public static class AiDeniedException extends RuntimeException {
        public final int code;

        public AiDeniedException(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    private final ForestAiProperties properties;
    private final ForestAiOrchestratorService orchestrator;
    private final AiConversationService conversationService;
    private final KnowledgeIngestionService ingestionService;
    private final AiAuditService auditService;

    public ForestAiChatService(ForestAiProperties properties, ForestAiOrchestratorService orchestrator,
                               AiConversationService conversationService, KnowledgeIngestionService ingestionService,
                               AiAuditService auditService) {
        this.properties = properties;
        this.orchestrator = orchestrator;
        this.conversationService = conversationService;
        this.ingestionService = ingestionService;
        this.auditService = auditService;
    }

    public Map<String, Object> chat(Principal principal, String conversationId, String message) {
        return orchestrator.chat(principal, conversationId, message);
    }

    public Map<String, Object> health() {
        return orchestrator.health();
    }

    public List<Map<String, Object>> listConversations(Principal principal) {
        return conversationService.listConversations(principal);
    }

    public Map<String, Object> getConversation(Principal principal, String externalId) throws AiDeniedException {
        Long internal = AiConversationService.parseExternalId(externalId);
        Map<String, Object> conversation = conversationService.getConversation(principal, internal);
        if (conversation == null) throw new AiDeniedException(404, "会话不存在或无权访问");
        return conversation;
    }

    public void deleteConversation(Principal principal, String externalId) throws AiDeniedException {
        Long internal = AiConversationService.parseExternalId(externalId);
        boolean deleted = conversationService.deleteOwnConversation(principal, internal);
        if (!deleted) throw new AiDeniedException(404, "会话不存在或只能清除自己的会话");
        auditService.record("conversation_delete", principal, internal, null, null, true, null, null);
    }

    public List<Map<String, Object>> knowledgeDocuments(Principal principal) {
        return ingestionService.documents();
    }

    /** 重建知识库：仅管理员。 */
    public String reindex(Principal principal, boolean force) {
        requireAdmin(principal);
        if (!properties.isEnabled()) return "AI 功能未启用，无法索引";
        auditService.record("knowledge_reindex", principal, null, null, null, true, null, null);
        return ingestionService.requestReindex(principal.username, force);
    }

    public Map<String, Object> uploadDocument(Principal principal, org.springframework.web.multipart.MultipartFile file) {
        requireAdmin(principal);
        Map<String, Object> result = ingestionService.storeUpload(file, principal.username);
        auditService.record("knowledge_upload", principal, null, null,
                resultMessageParams(result), true, null, null);
        return result;
    }

    private Map<String, Object> resultMessageParams(Map<String, Object> result) {
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("filename", result.get("filename"));
        params.put("relativePath", result.get("relativePath"));
        return params;
    }

    private void requireAdmin(Principal principal) {
        if (principal == null) throw new AiDeniedException(401, "请先登录");
        if (!principal.isAdmin()) throw new AiDeniedException(403, "仅管理员可以重建或导入知识库");
    }
}
