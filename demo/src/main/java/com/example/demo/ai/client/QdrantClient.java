package com.example.demo.ai.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.demo.ai.config.ForestAiProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Qdrant 向量库客户端（REST）。仅项目内部网络可达，无公网暴露。
 * 所有失败以 QdrantException 抛出，由上层降级为“无知识库检索”。
 */
@Component
public class QdrantClient {

    public static class QdrantException extends RuntimeException {
        public QdrantException(String message) { super(message); }
    }

    public static class Hit {
        public final double score;
        public final JSONObject payload;

        public Hit(double score, JSONObject payload) {
            this.score = score;
            this.payload = payload;
        }
    }

    private final ForestAiProperties properties;
    private final RestTemplate dataRestTemplate;
    private final RestTemplate probeRestTemplate;

    public QdrantClient(ForestAiProperties properties,
                        @Qualifier("aiDataRestTemplate") RestTemplate dataRestTemplate,
                        @Qualifier("aiProbeRestTemplate") RestTemplate probeRestTemplate) {
        this.properties = properties;
        this.dataRestTemplate = dataRestTemplate;
        this.probeRestTemplate = probeRestTemplate;
    }

    private String collectionUrl() {
        String base = properties.getQdrantUrl().trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/collections/" + properties.getQdrantCollection();
    }

    public boolean ping() {
        try {
            call(probeRestTemplate, HttpMethod.GET, properties.getQdrantUrl().trim() + "/readyz", null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean collectionExists() {
        try {
            call(dataRestTemplate, HttpMethod.GET, collectionUrl(), null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 向量点数量；集合不存在返回 0。 */
    public long pointCount() {
        try {
            String body = call(dataRestTemplate, HttpMethod.GET, collectionUrl(), null);
            JSONObject root = JSON.parseObject(body);
            JSONObject result = root == null ? null : root.getJSONObject("result");
            return result == null ? 0L : result.getLongValue("points_count");
        } catch (Exception e) {
            return 0L;
        }
    }

    public void ensureCollection(int dimension) {
        if (collectionExists()) return;
        Map<String, Object> vectors = new HashMap<>();
        vectors.put("size", dimension);
        vectors.put("distance", "Cosine");
        Map<String, Object> body = new HashMap<>();
        body.put("vectors", vectors);
        try {
            call(dataRestTemplate, HttpMethod.PUT, collectionUrl(), body);
        } catch (QdrantException e) {
            throw new QdrantException("创建向量集合失败，请确认 Qdrant 服务与磁盘状态");
        }
    }

    public void recreateCollection(int dimension) {
        try {
            call(dataRestTemplate, HttpMethod.DELETE, collectionUrl(), null);
        } catch (Exception ignored) {
            // 集合可能不存在
        }
        ensureCollection(dimension);
    }

    /**
     * upsert 一批点。points: [{id, vector, payload}]，id 用文档+块号派生的 UUID。
     */
    public void upsert(List<Map<String, Object>> points) {
        if (points.isEmpty()) return;
        Map<String, Object> body = new HashMap<>();
        body.put("points", points);
        call(dataRestTemplate, HttpMethod.PUT, collectionUrl() + "/points?wait=true", body);
    }

    /** 删除某文档的全部向量（按 payload.documentId 过滤）。 */
    public void deleteByDocument(long documentId) {
        List<Object> must = new ArrayList<>();
        must.add(condition("documentId", documentId));
        Map<String, Object> filter = new HashMap<>();
        filter.put("must", must);
        Map<String, Object> body = new HashMap<>();
        body.put("filter", filter);
        call(dataRestTemplate, HttpMethod.POST, collectionUrl() + "/points/delete?wait=true", body);
    }

    public List<Hit> search(double[] vector, int limit) {
        Map<String, Object> body = new HashMap<>();
        List<Double> values = new ArrayList<>(vector.length);
        for (double v : vector) values.add(v);
        body.put("vector", values);
        body.put("limit", Math.max(1, Math.min(limit, 20)));
        body.put("with_payload", true);
        String response = call(dataRestTemplate, HttpMethod.POST, collectionUrl() + "/points/search", body);
        JSONObject root = JSON.parseObject(response);
        JSONArray results = root == null ? null : root.getJSONArray("result");
        List<Hit> hits = new ArrayList<>();
        if (results != null) {
            for (int i = 0; i < results.size(); i++) {
                JSONObject item = results.getJSONObject(i);
                hits.add(new Hit(item.getDoubleValue("score"), item.getJSONObject("payload")));
            }
        }
        return hits;
    }

    private Map<String, Object> condition(String key, long value) {
        Map<String, Object> condition = new HashMap<>();
        condition.put("key", key);
        condition.put("match", singleValue(value));
        return condition;
    }

    private Map<String, Object> singleValue(long value) {
        Map<String, Object> match = new HashMap<>();
        match.put("value", value);
        return match;
    }

    private String call(RestTemplate template, HttpMethod method, String url, Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body == null ? null : JSON.toJSONString(body), headers);
            ResponseEntity<String> response = template.exchange(url, method, entity, String.class);
            String responseBody = response.getBody();
            // Qdrant 对失败请求也可能返回 200 + status=error，检查一次。
            if (responseBody != null && responseBody.contains("\"status\":\"error\"")) {
                throw new QdrantException("向量库返回错误");
            }
            return responseBody;
        } catch (QdrantException e) {
            throw e;
        } catch (Exception e) {
            throw new QdrantException("无法访问本地向量库");
        }
    }
}
