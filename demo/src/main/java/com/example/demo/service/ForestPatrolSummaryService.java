package com.example.demo.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将巡护遥测事实交给 OpenAI-compatible 大模型生成简洁总结。
 * 未配置接口或调用失败时使用规则总结，确保巡护结单主流程始终可用。
 */
@Service
public class ForestPatrolSummaryService {

    private static final Logger log = LoggerFactory.getLogger(ForestPatrolSummaryService.class);

    @Value("${forest.llm.api-url:}")
    private String apiUrl;

    @Value("${forest.llm.api-key:}")
    private String apiKey;

    @Value("${forest.llm.model:gpt-4o-mini}")
    private String model;

    private final RestTemplate restTemplate;

    public ForestPatrolSummaryService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public SummaryResult summarize(String facts, String fallback) {
        if (blank(apiUrl) || blank(apiKey)) {
            return new SummaryResult(fallback, "rules");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey.trim());

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(message("system", "你是森林公园防火巡护分析助手。只能依据输入数据生成120至200字中文总结，不得虚构火情、人员或设备信息。总结必须包含数据变化、风险判断和下一步建议，并说明本次数据为系统仿真演示数据。"));
            messages.add(message("user", facts));

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("temperature", 0.2D);
            body.put("messages", messages);

            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(body), headers);
            String response = restTemplate.postForObject(apiUrl.trim(), entity, String.class);
            JSONObject root = JSON.parseObject(response);
            JSONArray choices = root == null ? null : root.getJSONArray("choices");
            String content = choices == null || choices.isEmpty() ? "" : choices.getJSONObject(0).getJSONObject("message").getString("content");
            if (blank(content)) throw new IllegalStateException("大模型响应未包含总结文本");
            return new SummaryResult(content.trim(), "llm");
        } catch (Exception e) {
            log.warn("巡护大模型总结失败，已切换规则总结：{}", e.getMessage());
            return new SummaryResult(fallback, "rules");
        }
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> value = new HashMap<>();
        value.put("role", role);
        value.put("content", content);
        return value;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static class SummaryResult {
        public final String report;
        public final String source;

        public SummaryResult(String report, String source) {
            this.report = report;
            this.source = source;
        }
    }
}
