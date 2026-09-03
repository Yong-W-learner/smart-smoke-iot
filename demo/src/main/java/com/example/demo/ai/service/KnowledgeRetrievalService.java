package com.example.demo.ai.service;

import com.alibaba.fastjson2.JSONObject;
import com.example.demo.ai.client.OllamaClient;
import com.example.demo.ai.client.QdrantClient;
import com.example.demo.ai.config.ForestAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 检索：问题向量化 → Qdrant topK 相似度搜索。
 * Qdrant/Ollama 缺失时返回空结果并携带降级原因，由编排层继续走纯实时数据路径。
 */
@Service
public class KnowledgeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalService.class);
    /** 低于该相似度视为无关，避免把不相干文档塞进提示词。 */
    private static final double MIN_SCORE = 0.15D;
    private static final int MAX_SNIPPET_CHARS = 700;

    private final ForestAiProperties properties;
    private final OllamaClient ollamaClient;
    private final QdrantClient qdrantClient;

    public KnowledgeRetrievalService(ForestAiProperties properties, OllamaClient ollamaClient, QdrantClient qdrantClient) {
        this.properties = properties;
        this.ollamaClient = ollamaClient;
        this.qdrantClient = qdrantClient;
    }

    public static class RetrievalResult {
        public final List<JSONObject> hits = new ArrayList<>();
        public boolean degraded;
        public String degradedReason;
    }

    public RetrievalResult retrieve(String question, Integer topKOverride) {
        RetrievalResult result = new RetrievalResult();
        if (!properties.isEnabled()) {
            result.degraded = true;
            result.degradedReason = "AI 功能未启用";
            return result;
        }
        int topK = topKOverride == null ? properties.getTopK() : Math.max(1, Math.min(topKOverride, 20));
        try {
            if (!qdrantClient.collectionExists()) {
                result.degraded = true;
                result.degradedReason = "知识库尚未建立索引";
                return result;
            }
            double[] vector = ollamaClient.embed(properties.getEmbeddingModel(), Collections.singletonList(question)).get(0);
            List<QdrantClient.Hit> hits = qdrantClient.search(vector, topK);
            for (QdrantClient.Hit hit : hits) {
                if (hit.score < MIN_SCORE || hit.payload == null) continue;
                JSONObject item = new JSONObject(new LinkedHashMap<>());
                String text = hit.payload.getString("text");
                if (text != null && text.length() > MAX_SNIPPET_CHARS) text = text.substring(0, MAX_SNIPPET_CHARS) + "…";
                item.put("documentId", hit.payload.get("documentId"));
                item.put("filename", hit.payload.getString("filename"));
                item.put("relativePath", hit.payload.getString("relativePath"));
                item.put("section", hit.payload.getString("section"));
                item.put("chunkIndex", hit.payload.get("chunkIndex"));
                item.put("score", Math.round(hit.score * 1000D) / 1000D);
                item.put("text", text);
                result.hits.add(item);
            }
        } catch (OllamaClient.OllamaException e) {
            result.degraded = true;
            result.degradedReason = "本地嵌入模型不可用：" + e.getMessage();
            log.info("知识检索降级：{}", result.degradedReason);
        } catch (QdrantClient.QdrantException e) {
            result.degraded = true;
            result.degradedReason = "本地向量库不可用：" + e.getMessage();
            log.info("知识检索降级：{}", result.degradedReason);
        } catch (Exception e) {
            result.degraded = true;
            result.degradedReason = "知识检索暂不可用";
            log.warn("知识检索异常：{}", e.toString());
        }
        return result;
    }

    /** 拼接进提示词的知识上下文（标记为不可信参考资料）。 */
    public static String formatContext(RetrievalResult retrieval) {
        if (retrieval.hits.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int number = 1;
        for (JSONObject hit : retrieval.hits) {
            sb.append("[K").append(number++).append("] 来源《").append(hit.getString("filename"));
            String section = hit.getString("section");
            if (section != null && !section.isEmpty()) sb.append(" · ").append(section);
            sb.append("》\n").append(hit.getString("text")).append("\n\n");
        }
        return sb.toString().trim();
    }

    /** 前端 sources：知识类来源去重展示。 */
    public static List<Map<String, Object>> toSources(RetrievalResult retrieval) {
        List<Map<String, Object>> sources = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (JSONObject hit : retrieval.hits) {
            String name = hit.getString("filename");
            if (name == null || seen.containsKey(name)) continue;
            seen.put(name, true);
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("type", "knowledge");
            source.put("name", name);
            source.put("section", hit.getString("section"));
            sources.add(source);
        }
        return sources;
    }
}
