package com.example.demo.ai.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.demo.ai.config.ForestAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ollama 客户端：仅通过 OpenAI 兼容接口访问本机 Docker 内的 Ollama，
 * 不调用任何云端服务。apiKey 只是协议占位符。所有失败都转成可降级的异常。
 */
@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    /** 上游不可用/响应异常时抛出的受检外异常，消息已做脱敏（不含 URL 与内部堆栈）。 */
    public static class OllamaException extends RuntimeException {
        public OllamaException(String message) { super(message); }
        public OllamaException(String message, Throwable cause) { super(message, cause); }
    }

    private final ForestAiProperties properties;
    private final RestTemplate chatRestTemplate;
    private final RestTemplate dataRestTemplate;
    private final RestTemplate probeRestTemplate;

    public OllamaClient(ForestAiProperties properties,
                        @Qualifier("aiChatRestTemplate") RestTemplate chatRestTemplate,
                        @Qualifier("aiDataRestTemplate") RestTemplate dataRestTemplate,
                        @Qualifier("aiProbeRestTemplate") RestTemplate probeRestTemplate) {
        this.properties = properties;
        this.chatRestTemplate = chatRestTemplate;
        this.dataRestTemplate = dataRestTemplate;
        this.probeRestTemplate = probeRestTemplate;
    }

    /** 服务是否可访问（health 接口使用，快速失败）。 */
    public boolean ping() {
        try {
            exchange(probeRestTemplate, properties.modelsUrl());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 已安装模型列表；服务不可达时抛 OllamaException。 */
    public List<String> models() {
        String body = exchange(dataRestTemplate, properties.modelsUrl());
        JSONObject root = parse(body);
        JSONArray data = root.getJSONArray("data");
        List<String> names = new ArrayList<>();
        if (data != null) {
            for (int i = 0; i < data.size(); i++) {
                String id = data.getJSONObject(i).getString("id");
                if (id != null && !id.isEmpty()) names.add(id);
            }
        }
        return names;
    }

    public boolean hasModel(String model) {
        try {
            return modelInstalled(models(), model);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean modelInstalled(List<String> installed, String model) {
        for (String name : installed) {
            if (name.equals(model) || name.equals(model + ":latest")
                    || name.startsWith(model + ":") && model.indexOf(':') < 0) return true;
        }
        return false;
    }

    /**
     * 对话补全。jsonMode=true 时请求 response_format=json_object，
     * 若服务端版本不支持则自动去掉该参数重试一次。
     */
    public String chat(String model, List<Map<String, String>> messages, Double temperature, boolean jsonMode) {
        Map<String, Object> body = chatBody(model, messages, temperature, jsonMode);
        String response;
        try {
            response = postWith(chatRestTemplate, properties.resolvedChatUrl(), body);
        } catch (OllamaException e) {
            if (jsonMode) {
                log.info("response_format 不被支持或调用失败，去掉该参数重试一次");
                response = postWith(chatRestTemplate, properties.resolvedChatUrl(), chatBody(model, messages, temperature, false));
            } else {
                throw e;
            }
        }
        JSONObject root = parse(response);
        JSONArray choices = root.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) throw new OllamaException("模型响应缺少 choices");
        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        String content = message == null ? null : message.getString("content");
        if (content == null || content.trim().isEmpty()) throw new OllamaException("模型响应内容为空");
        return content;
    }

    private Map<String, Object> chatBody(String model, List<Map<String, String>> messages, Double temperature, boolean jsonMode) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature == null ? 0.2D : temperature);
        body.put("stream", false);
        // Ollama 默认上下文只有 4096 token，知识库+工具数据需要更大窗口，否则会被悄悄截断。
        Map<String, Object> options = new HashMap<>();
        options.put("num_ctx", 8192);
        body.put("options", options);
        if (jsonMode) {
            Map<String, Object> format = new HashMap<>();
            format.put("type", "json_object");
            body.put("response_format", format);
        }
        return body;
    }

    /** 批量文本嵌入；返回顺序与输入一致。嵌入接口跨版本差异大，按路由链自动探测。 */
    public List<double[]> embed(String model, List<String> texts) {
        List<double[]> vectors = new ArrayList<>();
        if (texts == null || texts.isEmpty()) return vectors;
        String route = embedRoutes.getOrDefault(model, ROUTE_OPENAI);
        String firstError = null;
        for (int offset = 0; offset < texts.size(); offset += 8) {
            List<String> batch = texts.subList(offset, Math.min(texts.size(), offset + 8));
            EmbedAttempt attempt = tryEmbed(model, batch, route);
            if (attempt.vectors == null) {
                if (firstError == null) firstError = attempt.error;
                for (String candidate : new String[] {ROUTE_OPENAI, ROUTE_NATIVE, ROUTE_LEGACY}) {
                    if (candidate.equals(route)) continue;
                    attempt = tryEmbed(model, batch, candidate);
                    if (attempt.vectors != null) {
                        log.info("嵌入接口自动切换：模型 {} 改走 {} 路由", model, candidate);
                        route = candidate;
                        embedRoutes.put(model, route);
                        break;
                    }
                }
            }
            if (attempt.vectors == null) {
                embedRoutes.remove(model);
                throw new OllamaException(firstError == null ? "嵌入服务暂不可用" : firstError);
            }
            vectors.addAll(attempt.vectors);
        }
        return vectors;
    }

    private static final String ROUTE_OPENAI = "openai";
    private static final String ROUTE_NATIVE = "native";
    private static final String ROUTE_LEGACY = "legacy";
    private final Map<String, String> embedRoutes = new java.util.concurrent.ConcurrentHashMap<>();

    private static final class EmbedAttempt {
        List<double[]> vectors;
        String error;
    }

    private EmbedAttempt tryEmbed(String model, List<String> batch, String route) {
        EmbedAttempt attempt = new EmbedAttempt();
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            String url;
            if (ROUTE_NATIVE.equals(route)) {
                url = properties.ollamaRoot() + "/api/embed";
                body.put("input", batch);
            } else if (ROUTE_LEGACY.equals(route)) {
                url = null;
                body.put("prompt", batch.get(0));
            } else {
                url = properties.embeddingsUrl();
                body.put("input", batch);
            }
            List<double[]> vectors = new ArrayList<>();
            if (ROUTE_LEGACY.equals(route)) {
                // 旧版单条接口：逐个嵌入（仅作为最后兜底）。
                for (String text : batch) {
                    body.put("prompt", text);
                    JSONObject root = parse(post(properties.ollamaRoot() + "/api/embeddings", body));
                    vectors.add(toVector(root.getJSONArray("embedding"), route));
                }
                attempt.vectors = vectors;
                return attempt;
            }
            JSONObject root = parse(post(url, body));
            if (ROUTE_NATIVE.equals(route)) {
                JSONArray data = root.getJSONArray("embeddings");
                if (data == null || data.size() < batch.size()) throw new OllamaException("嵌入响应数量不匹配");
                for (int i = 0; i < data.size(); i++) vectors.add(toVector(data.getJSONArray(i), route));
            } else {
                JSONArray data = root.getJSONArray("data");
                if (data == null || data.size() < batch.size()) throw new OllamaException("嵌入响应数量不匹配");
                for (int i = 0; i < data.size(); i++) vectors.add(toVector(data.getJSONObject(i).getJSONArray("embedding"), route));
            }
            attempt.vectors = vectors;
        } catch (OllamaException e) {
            attempt.error = e.getMessage();
        }
        return attempt;
    }

    private double[] toVector(JSONArray vector, String route) {
        if (vector == null || vector.isEmpty()) throw new OllamaException("嵌入响应为空向量（" + route + "）");
        double[] values = new double[vector.size()];
        for (int j = 0; j < vector.size(); j++) values[j] = vector.getDoubleValue(j);
        return values;
    }

    private String post(String url, Map<String, Object> body) {
        return postWith(dataRestTemplate, url, body);
    }

    private String postWith(RestTemplate template, String url, Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(properties.getApiKey() == null || properties.getApiKey().trim().isEmpty()
                    ? "ollama" : properties.getApiKey().trim());
            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(body), headers);
            return template.postForObject(url, entity, String.class);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            throw new OllamaException("AI 服务返回错误状态 " + e.getRawStatusCode());
        } catch (Exception e) {
            throw new OllamaException("无法访问本地 AI 服务");
        }
    }

    private String exchange(RestTemplate template, String url) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (properties.getApiKey() != null && !properties.getApiKey().trim().isEmpty()) {
                headers.setBearerAuth(properties.getApiKey().trim());
            }
            String body = template.exchange(url, org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
            if (body == null) throw new OllamaException("AI 服务返回空响应");
            return body;
        } catch (OllamaException e) {
            throw e;
        } catch (Exception e) {
            throw new OllamaException("无法访问本地 AI 服务");
        }
    }

    private JSONObject parse(String body) {
        try {
            JSONObject root = JSON.parseObject(body);
            if (root == null) throw new OllamaException("AI 服务响应无法解析");
            return root;
        } catch (OllamaException e) {
            throw e;
        } catch (Exception e) {
            throw new OllamaException("AI 服务响应无法解析");
        }
    }
}
