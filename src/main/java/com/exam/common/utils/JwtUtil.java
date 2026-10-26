package com.exam.common.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类 —— 双 Token 方案
 *
 * <p>Access Token：短期（30分钟），携带用户身份
 * Refresh Token：长期（7天），用于续签 Access Token</p>
 */
@Component
public class JwtUtil {

    /**
     * Token 类型声明。
     *
     * <p><b>为什么必须有这个 claim</b>：Access Token 与 Refresh Token 原本
     * 使用完全相同的 claims，只有过期时间不同——这意味着校验方无法分辨二者。
     * 后果是：拿一个 30 分钟的 Access Token 去调 refresh 接口，
     * 会被当成合法的 Refresh Token 接受，从而换出一整套全新的 Token
     * （含 7 天的 Refresh Token）。「Access Token 短命」这个设计前提就被绕过了。
     *
     * <p>加上类型声明后，refresh 接口可以明确拒绝 Access Token。
     */
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private static final String CLAIM_TYPE = "typ";

    private final SecretKey secretKey;
    private final long accessExpire;
    private final long refreshExpire;

    public JwtUtil(@Value("${jwt.secret:exam-system-secret-key-must-be-at-least-256-bits-long}") String secret,
                   @Value("${jwt.access-expire:1800000}") long accessExpire,
                   @Value("${jwt.refresh-expire:604800000}") long refreshExpire) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpire = accessExpire;
        this.refreshExpire = refreshExpire;
    }

    /** 生成 Access Token */
    public String generateAccessToken(Long userId, String username, Integer role) {
        return generateToken(userId, username, role, accessExpire, TYPE_ACCESS);
    }

    /** 生成 Refresh Token */
    public String generateRefreshToken(Long userId, String username, Integer role) {
        return generateToken(userId, username, role, refreshExpire, TYPE_REFRESH);
    }

    private String generateToken(Long userId, String username, Integer role, long expire, String type) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim(CLAIM_TYPE, type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expire))
                .signWith(secretKey)
                .compact();
    }

    /** 解析 Token */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /** 从 Token 中提取用户 ID */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return claims != null ? Long.parseLong(claims.getSubject()) : null;
    }

    /** 从 Token 中提取用户名 */
    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("username", String.class) : null;
    }

    /** 从 Token 中提取角色 */
    public Integer getRole(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("role", Integer.class) : null;
    }

    /** 校验 Token 是否有效 */
    public boolean validate(String token) {
        return parseToken(token) != null;
    }

    /** 取出 Token 的类型声明（access / refresh）；无法解析时返回 null */
    public String getTokenType(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get(CLAIM_TYPE, String.class) : null;
    }

    /** 是否为 Refresh Token（类型正确且签名有效） */
    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(getTokenType(token));
    }

    /** 是否为 Access Token（类型正确且签名有效） */
    public boolean isAccessToken(String token) {
        return TYPE_ACCESS.equals(getTokenType(token));
    }
}