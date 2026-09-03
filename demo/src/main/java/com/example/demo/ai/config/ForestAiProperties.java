package com.example.demo.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * forest.ai.* 配置绑定。所有值均有安全默认：Ollama/Qdrant 不存在时
 * 只是健康检查失败与对话降级，绝不影响应用启动与其他业务。
 */
@Component
@ConfigurationProperties(prefix = "forest.ai")
public class ForestAiProperties {

    /** AI 助手总开关，FOREST_AI_ENABLED=false 可整体关闭。 */
    private boolean enabled = true;
    /** OpenAI 兼容基地址，例如 http://ollama:11434/v1。 */
    private String baseUrl = "http://127.0.0.1:11434/v1";
    /** chat completions 完整地址；留空则取 baseUrl + /chat/completions。 */
    private String chatUrl = "";
    /** Ollama 不校验该值，仅是 OpenAI 协议占位符，不是真实密钥。 */
    private String apiKey = "ollama";
    private String chatModel = "qwen3:4b";
    private String embeddingModel = "qwen3-embedding:0.6b";
    private String qdrantUrl = "http://127.0.0.1:6333";
    private String qdrantCollection = "forest_knowledge";
    private int timeoutSeconds = 120;
    private int maxContextMessages = 10;
    private int topK = 5;
    private int maxInputLength = 2000;
    private int rateLimitPerMinute = 10;
    private int maxConcurrentCalls = 2;
    /** 知识库扫描目录（Docker 内挂载 ../knowledge）。 */
    private String knowledgeDir = "../knowledge";
    /** 管理员上传文档保存目录（Docker 内为独立数据卷）。 */
    private String uploadDir = "../knowledge-uploads";
    /** 上传文件大小上限（MB）。 */
    private int maxUploadMb = 5;

    public long getMaxUploadBytes() {
        return (long) maxUploadMb * 1024L * 1024L;
    }

    public int getMaxUploadMb() { return maxUploadMb; }
    public void setMaxUploadMb(int maxUploadMb) { this.maxUploadMb = maxUploadMb <= 0 ? 5 : Math.min(maxUploadMb, 20); }

    public String resolvedChatUrl() {
        String base = baseUrl == null ? "" : baseUrl.trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (chatUrl != null && !chatUrl.trim().isEmpty()) return chatUrl.trim();
        return base + "/chat/completions";
    }

    public String embeddingsUrl() {
        return trimBase() + "/embeddings";
    }

    public String modelsUrl() {
        return trimBase() + "/models";
    }

    /** 去掉 /v1 前缀，得到 Ollama 服务根地址（仅用于展示与探活）。 */
    public String ollamaRoot() {
        String base = trimBase();
        return base.endsWith("/v1") ? base.substring(0, base.length() - 3) : base;
    }

    private String trimBase() {
        String base = baseUrl == null ? "" : baseUrl.trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getChatUrl() { return chatUrl; }
    public void setChatUrl(String chatUrl) { this.chatUrl = chatUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getChatModel() { return chatModel; }
    public void setChatModel(String chatModel) { this.chatModel = chatModel; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public String getQdrantUrl() { return qdrantUrl; }
    public void setQdrantUrl(String qdrantUrl) { this.qdrantUrl = qdrantUrl; }
    public String getQdrantCollection() { return qdrantCollection; }
    public void setQdrantCollection(String qdrantCollection) { this.qdrantCollection = qdrantCollection; }
    public int getTimeoutSeconds() { return Math.max(5, timeoutSeconds); }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getMaxContextMessages() { return Math.max(0, maxContextMessages); }
    public void setMaxContextMessages(int maxContextMessages) { this.maxContextMessages = maxContextMessages; }
    public int getTopK() { return topK <= 0 ? 5 : Math.min(topK, 20); }
    public void setTopK(int topK) { this.topK = topK; }
    public int getMaxInputLength() { return maxInputLength <= 0 ? 2000 : maxInputLength; }
    public void setMaxInputLength(int maxInputLength) { this.maxInputLength = maxInputLength; }
    public int getRateLimitPerMinute() { return rateLimitPerMinute <= 0 ? 10 : rateLimitPerMinute; }
    public void setRateLimitPerMinute(int rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }
    public int getMaxConcurrentCalls() { return maxConcurrentCalls <= 0 ? 2 : maxConcurrentCalls; }
    public void setMaxConcurrentCalls(int maxConcurrentCalls) { this.maxConcurrentCalls = maxConcurrentCalls; }
    public String getKnowledgeDir() { return knowledgeDir; }
    public void setKnowledgeDir(String knowledgeDir) { this.knowledgeDir = knowledgeDir; }
    public String getUploadDir() { return uploadDir; }
    public void setUploadDir(String uploadDir) { this.uploadDir = uploadDir; }
}
