package com.example.demo.ai.service;

import com.alibaba.fastjson2.JSON;
import com.example.demo.ai.support.AiAuthService.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * AI 操作审计：所有对话与工具调用留痕。参数在入库前清洗，
 * 禁止出现密码、token、Cookie、API Key、完整认证头与超长内容。
 */
@Service
public class AiAuditService {

    private static final Logger log = LoggerFactory.getLogger(AiAuditService.class);
    private static final Pattern SENSITIVE_KEY =
            Pattern.compile("(?i).*(password|passwd|secret|token|authorization|cookie|api[-_]?key|credential).*");

    private final JdbcTemplate jdbc;

    public AiAuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String action, Principal principal, Long conversationId, String toolName,
                       Map<String, Object> parameters, boolean success, String errorMessage, Long latencyMs) {
        try {
            jdbc.update("INSERT INTO ai_audit_log(user_id,username,conversation_id,action,tool_name,parameters_json,success,error_message,latency_ms) VALUES(?,?,?,?,?,?,?,?,?)",
                    principal == null ? null : principal.userId,
                    principal == null ? null : truncate(principal.username, 64),
                    conversationId,
                    truncate(action, 32),
                    toolName == null ? null : truncate(toolName, 64),
                    sanitizeParameters(parameters),
                    success ? 1 : 0,
                    errorMessage == null ? null : truncate(errorMessage, 500),
                    latencyMs);
        } catch (Exception e) {
            // 审计失败绝不阻断业务，只落服务器日志。
            log.warn("AI 审计写入失败：{}", e.getMessage());
        }
    }

    static String sanitizeParameters(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) return null;
        Map<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (SENSITIVE_KEY.matcher(entry.getKey()).matches()) {
                safe.put(entry.getKey(), "[redacted]");
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Map || value instanceof Iterable || value instanceof Number || value instanceof Boolean) {
                safe.put(entry.getKey(), value);
            } else if (value != null) {
                safe.put(entry.getKey(), truncate(String.valueOf(value), 200));
            }
        }
        String json;
        try {
            json = JSON.toJSONString(safe);
        } catch (Exception e) {
            return null;
        }
        return json.length() > 1900 ? json.substring(0, 1900) + "...(truncated)" : json;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
