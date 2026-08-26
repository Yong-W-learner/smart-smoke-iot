package com.example.demo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    /**
     * JWT 签名密钥
     */
    @Value("${jwt.secret}")
    private String secret;

    /**
     * JWT 有效时间，单位：毫秒
     */
    @Value("${jwt.expiration}")
    private Long expiration;


    /**
     * ==========================================
     * 生成 JWT
     * ==========================================
     *
     * JWT 中保存：
     * userId
     * username
     * role
     */
    public String generateToken(
            Long userId,
            String username,
            String role) {

        Date now = new Date();

        Date expireTime =
                new Date(
                        now.getTime() + expiration
                );

        return Jwts.builder()

                /*
                 * subject 保存用户名
                 */
                .setSubject(username)

                /*
                 * 自定义用户信息
                 */
                .claim("userId", userId)

                .claim("role", role)

                /*
                 * JWT 签发时间
                 */
                .setIssuedAt(now)

                /*
                 * JWT 过期时间
                 */
                .setExpiration(expireTime)

                /*
                 * 使用密钥签名
                 */
                .signWith(
                        getSigningKey(),
                        SignatureAlgorithm.HS256
                )

                .compact();
    }


    /**
     * ==========================================
     * 解析 JWT
     * ==========================================
     */
    public Claims parseToken(String token) {

        return Jwts.parserBuilder()

                .setSigningKey(
                        getSigningKey()
                )

                .build()

                .parseClaimsJws(token)

                .getBody();
    }


    /**
     * 从 JWT 获取用户名
     */
    public String getUsername(String token) {

        return parseToken(token)
                .getSubject();
    }


    /**
     * 从 JWT 获取用户 ID
     */
    public Long getUserId(String token) {

        Object value =
                parseToken(token)
                        .get("userId");

        if (value == null) {
            return null;
        }

        /*
         * JWT JSON 反序列化以后，
         * 数字可能是 Integer 或 Long，
         * 所以统一通过 Number 处理。
         */
        if (value instanceof Number) {

            return ((Number) value)
                    .longValue();
        }

        return Long.valueOf(
                value.toString()
        );
    }


    /**
     * 从 JWT 获取用户角色
     */
    public String getRole(String token) {

        Object value =
                parseToken(token)
                        .get("role");

        return value == null
                ? null
                : value.toString();
    }


    /**
     * ==========================================
     * 判断 JWT 是否有效
     * ==========================================
     */
    public boolean validateToken(String token) {

        try {

            Claims claims =
                    parseToken(token);

            Date expiration =
                    claims.getExpiration();

            return expiration != null
                    && expiration.after(
                    new Date()
            );

        } catch (Exception e) {

            /*
             * 包括：
             *
             * Token 被篡改
             * Token 格式错误
             * Token 已过期
             * 签名错误
             *
             * 都认为无效。
             */
            return false;
        }
    }


    /**
     * 获取 JWT 签名密钥
     */
    private SecretKey getSigningKey() {

        return Keys.hmacShaKeyFor(
                secret.getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }
}