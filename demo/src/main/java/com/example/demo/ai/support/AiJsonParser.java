package com.example.demo.ai.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析模型返回的结构化结果。小模型经常带解释文字或 Markdown 围栏，
 * 这里做容错：剥围栏、截取最外层 JSON、失败时再尝试补全，仍失败则降级为纯文本。
 */
public final class AiJsonParser {

    private static final Pattern THINK_BLOCK = Pattern.compile("(?s)<think>.*?</think>");
    private static final Pattern ANSWER_FALLBACK = Pattern.compile("\"answer\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private AiJsonParser() {
    }

    public static class Parsed {
        public String answer = "";
        public String confidence = "low";
        public boolean needHumanReview;
        public boolean outOfScope;
        public final List<JSONObject> suggestedActions = new ArrayList<>();
        /** true 表示未拿到合法 JSON，answer 是原始文本降级。 */
        public boolean repaired;

        public boolean isValid() {
            return answer != null && !answer.trim().isEmpty();
        }
    }

    /** 允许前端跳转的既有页面（建议操作白名单，防止模型构造任意路径）。 */
    private static final String[] ALLOWED_TARGETS = {
            "/visitor-alert", "/login", "/mobile", "/ranger/ai", "/ranger/equipment", "/ranger"
    };

    public static Parsed parse(String raw) {
        Parsed parsed = new Parsed();
        if (raw == null || raw.trim().isEmpty()) return parsed;
        String cleaned = THINK_BLOCK.matcher(raw).replaceAll("").trim();
        cleaned = stripFences(cleaned);
        JSONObject object = extractJson(cleaned);
        if (object == null) {
            Matcher matcher = ANSWER_FALLBACK.matcher(cleaned);
            if (matcher.find()) {
                parsed.answer = unescape(matcher.group(1));
                parsed.repaired = true;
            } else {
                parsed.answer = cleaned;
                parsed.repaired = true;
            }
            parsed.needHumanReview = true;
            return parsed;
        }
        parsed.answer = object.getString("answer") == null ? "" : object.getString("answer").trim();
        String confidence = object.getString("confidence");
        parsed.confidence = "high".equals(confidence) || "medium".equals(confidence) ? confidence : "low";
        parsed.needHumanReview = !Boolean.FALSE.equals(object.getBoolean("needHumanReview"));
        parsed.outOfScope = Boolean.TRUE.equals(object.getBoolean("outOfScope"));
        JSONArray actions = object.getJSONArray("suggestedActions");
        if (actions != null) {
            for (int i = 0; i < actions.size() && parsed.suggestedActions.size() < 4; i++) {
                JSONObject action = actions.getJSONObject(i);
                if (action == null) continue;
                String type = action.getString("type");
                String target = action.getString("target");
                String label = action.getString("label");
                if (!"navigate".equals(type) || !targetAllowed(target) || label == null || label.trim().isEmpty()) continue;
                JSONObject safe = new JSONObject();
                safe.put("label", label.trim());
                safe.put("type", "navigate");
                safe.put("target", target.trim());
                parsed.suggestedActions.add(safe);
            }
        }
        if (parsed.answer.isEmpty()) {
            parsed.answer = cleaned;
            parsed.repaired = true;
        }
        return parsed;
    }

    public static boolean targetAllowed(String target) {
        if (target == null) return false;
        String value = target.trim();
        if (value.isEmpty() || value.contains("..") || value.contains("\\")) return false;
        for (String allowed : ALLOWED_TARGETS) {
            if (value.equals(allowed) || value.startsWith(allowed + "/")) return true;
        }
        return false;
    }

    private static String stripFences(String text) {
        String value = text.trim();
        if (value.startsWith("```")) {
            int firstBreak = value.indexOf('\n');
            if (firstBreak > 0) value = value.substring(firstBreak + 1);
            int fenceEnd = value.lastIndexOf("```");
            if (fenceEnd >= 0) value = value.substring(0, fenceEnd);
        }
        return value.trim();
    }

    private static JSONObject extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        String candidate = text.substring(start, end + 1);
        JSONObject direct = tryParse(candidate);
        if (direct != null) return direct;
        // 一次修复尝试：去掉尾逗号后重试。
        String repaired = candidate.replaceAll(",\\s*([}\\]])", "$1");
        return tryParse(repaired);
    }

    private static JSONObject tryParse(String candidate) {
        try {
            Object value = JSON.parse(candidate);
            return value instanceof JSONObject ? (JSONObject) value : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String unescape(String value) {
        try {
            return JSON.parseObject("{\"v\":\"" + value + "\"}").getString("v");
        } catch (Exception e) {
            return value;
        }
    }
}
