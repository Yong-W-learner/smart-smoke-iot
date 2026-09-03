package com.example.demo.ai.service;

import com.example.demo.ai.support.AiAuthService.Principal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * 对话与消息持久化。会话 ID 对外为 "c-<数字>" 字符串，归属校验以 user_id 为准，
 * 普通用户只能读写自己的会话；管理员可查看会话元信息用于维护。
 */
@Service
public class AiConversationService {

    private final JdbcTemplate jdbc;

    public AiConversationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static String externalId(long id) {
        return "c-" + id;
    }

    /** 解析外部会话 ID；非法返回 null。 */
    public static Long parseExternalId(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.startsWith("c-")) trimmed = trimmed.substring(2);
        if (!trimmed.matches("^\\d{1,19}$")) return null;
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 取得或创建会话；返回内部数字 id。 */
    public long ensureConversation(Principal principal, String externalConversationId, String firstQuestion) {
        Long parsed = parseExternalId(externalConversationId);
        if (parsed != null) {
            Integer owned = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ai_conversation WHERE id=? AND user_id=?", Integer.class, parsed, principal.userId);
            if (owned != null && owned > 0) {
                jdbc.update("UPDATE ai_conversation SET update_time=NOW() WHERE id=?", parsed);
                return parsed;
            }
        }
        String title = firstQuestion == null ? "新的对话" : firstQuestion.trim();
        if (title.length() > 40) title = title.substring(0, 40) + "…";
        if (title.isEmpty()) title = "新的对话";
        final String finalTitle = title;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO ai_conversation(user_id,username,title) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, principal.userId);
            statement.setString(2, principal.username);
            statement.setString(3, finalTitle);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    public void appendMessage(long conversationId, String role, String content, String sourceJson, String model, Long latencyMs) {
        jdbc.update("INSERT INTO ai_message(conversation_id,role,content,source_json,model_name,latency_ms) VALUES(?,?,?,?,?,?)",
                conversationId, role, content, sourceJson, model, latencyMs);
        jdbc.update("UPDATE ai_conversation SET update_time=NOW() WHERE id=?", conversationId);
    }

    /** 最近的对话消息（时间正序），用于拼装上下文。 */
    public List<Map<String, Object>> recentMessages(long conversationId, int limit) {
        if (limit <= 0) return java.util.Collections.emptyList();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT role,content,create_time AS createTime FROM ai_message WHERE conversation_id=?" +
                        " ORDER BY id DESC LIMIT " + Math.min(limit, 50), conversationId);
        java.util.Collections.reverse(rows);
        return rows;
    }

    public List<Map<String, Object>> listConversations(Principal principal) {
        if (principal.isAdmin()) {
            return jdbc.queryForList("SELECT id,user_id AS userId,username,title," +
                    "DATE_FORMAT(create_time,'%Y-%m-%d %H:%i') AS createTime,DATE_FORMAT(update_time,'%Y-%m-%d %H:%i') AS updateTime" +
                    " FROM ai_conversation ORDER BY update_time DESC LIMIT 100");
        }
        return jdbc.queryForList("SELECT id,user_id AS userId,username,title," +
                "DATE_FORMAT(create_time,'%Y-%m-%d %H:%i') AS createTime,DATE_FORMAT(update_time,'%Y-%m-%d %H:%i') AS updateTime" +
                " FROM ai_conversation WHERE user_id=? ORDER BY update_time DESC LIMIT 100", principal.userId);
    }

    /** 读取会话与消息；非本人（且非管理员）返回 null。 */
    public Map<String, Object> getConversation(Principal principal, Long internalId) {
        if (internalId == null) return null;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,user_id AS userId,username,title," +
                        "DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') AS createTime FROM ai_conversation WHERE id=?", internalId);
        if (rows.isEmpty()) return null;
        Map<String, Object> conversation = rows.get(0);
        long ownerId = ((Number) conversation.get("userId")).longValue();
        if (ownerId != principal.userId && !principal.isAdmin()) return null;
        conversation.put("messages", jdbc.queryForList(
                "SELECT id,role,content,source_json AS sourceJson,model_name AS modelName,latency_ms AS latencyMs," +
                        "DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') AS createTime" +
                        " FROM ai_message WHERE conversation_id=? ORDER BY id ASC LIMIT 200", internalId));
        conversation.put("id", externalId(internalId));
        return conversation;
    }

    /** 仅允许清除自己的会话。 */
    public boolean deleteOwnConversation(Principal principal, Long internalId) {
        if (internalId == null) return false;
        int messages = jdbc.update("DELETE m FROM ai_message m JOIN ai_conversation c ON c.id=m.conversation_id" +
                " WHERE c.id=? AND c.user_id=?", internalId, principal.userId);
        int conversations = jdbc.update("DELETE FROM ai_conversation WHERE id=? AND user_id=?", internalId, principal.userId);
        return messages >= 0 && conversations > 0;
    }
}
