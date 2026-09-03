package com.example.demo.ai.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * AI 模块身份与权限：沿用系统现有登录约定（Authorization: Bearer mock-token-<username>），
 * 并在 sys_user 中核验用户存在与角色；知识库导入/重建仅允许 role=admin。
 * 这里不引入新登录体系，也不改变现有护林员登录逻辑。
 */
@Component
public class AiAuthService {

    public static final String TOKEN_PREFIX = "mock-token-";
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_RANGER = "ranger";

    private final JdbcTemplate jdbc;

    public AiAuthService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static class Principal {
        public final Long userId;
        public final String username;
        public final String role;

        public Principal(Long userId, String username, String role) {
            this.userId = userId;
            this.username = username;
            this.role = role;
        }

        public boolean isAdmin() {
            return ROLE_ADMIN.equals(role);
        }
    }

    /** 从请求头解析用户名；格式不符返回 null（不抛异常，由控制器转成 401 语义）。 */
    public static String usernameFromToken(String authorization) {
        if (authorization == null) return null;
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) value = value.substring(7).trim();
        if (!value.startsWith(TOKEN_PREFIX)) return null;
        String username = value.substring(TOKEN_PREFIX.length()).trim();
        if (username.isEmpty() || !username.matches("^[\\u4e00-\\u9fa5A-Za-z0-9_\\-.]{1,64}$")) return null;
        return username;
    }

    /** 解析并核验当前用户；token 无效或用户不存在返回 null。 */
    public Principal current(HttpServletRequest request) {
        String username = usernameFromToken(request.getHeader("Authorization"));
        return username == null ? null : findByUsername(username);
    }

    public Principal findByUsername(String username) {
        if (username == null) return null;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,username,role FROM sys_user WHERE username=?", username);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        String role = row.get("role") == null ? ROLE_RANGER : String.valueOf(row.get("role"));
        if (!ROLE_ADMIN.equals(role) && !ROLE_RANGER.equals(role)) return null;
        return new Principal(((Number) row.get("id")).longValue(), username, role);
    }
}
