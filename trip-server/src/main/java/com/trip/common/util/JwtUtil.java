package com.trip.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具（JJWT 0.12.x，API 与 0.11 完全不同）。
 * 密钥来自配置 trip.jwt.secret，HS256 要求 ≥ 32 字节。
 */
@Component
public class JwtUtil {

    public static final String TOKEN_PREFIX = "Bearer ";

    private final SecretKey key;
    private final long accessTokenExpire;
    private final long refreshTokenExpire;

    public JwtUtil(@Value("${trip.jwt.secret}") String secret,
                   @Value("${trip.jwt.access-token-expire}") long accessTokenExpire,
                   @Value("${trip.jwt.refresh-token-expire}") long refreshTokenExpire) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpire = accessTokenExpire;
        this.refreshTokenExpire = refreshTokenExpire;
    }

    public long getAccessTokenExpire() {
        return accessTokenExpire;
    }

    public String createAccessToken(Long userId, String role) {
        return createAccessToken(userId, role, UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    public String createRefreshToken(Long userId, String role) {
        return createRefreshToken(userId, role, UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    public long getRefreshTokenExpire() { return refreshTokenExpire; }

    public String createAccessToken(Long userId, String role, String sessionId, String version) {
        return createToken(userId, role, accessTokenExpire, "access", sessionId, version);
    }

    public String createRefreshToken(Long userId, String role, String sessionId, String version) {
        return createToken(userId, role, refreshTokenExpire, "refresh", sessionId, version);
    }

    private String createToken(Long userId, String role, long expireSeconds, String type, String sessionId, String version) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim("type", type)
                .claim("sid", sessionId)
                .claim("version", version)
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expireSeconds * 1000))
                .signWith(key)
                .compact();
    }

    /** 解析 token；非法 / 过期抛出 JwtException。 */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }
}
