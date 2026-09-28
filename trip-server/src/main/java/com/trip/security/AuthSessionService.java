package com.trip.security;

import com.trip.common.exception.BizException;
import com.trip.common.util.JwtUtil;
import com.trip.module.user.entity.SysUser;
import com.trip.module.user.mapper.SysUserMapper;
import com.trip.module.user.vo.LoginVO;
import com.trip.module.user.vo.UserVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** A missing Redis session always denies access; clearing Redis cannot revive revoked tokens. */
@Service
@RequiredArgsConstructor
public class AuthSessionService {
    private static final DefaultRedisScript<Long> ROTATE = new DefaultRedisScript<>(
            "if redis.call('GET',KEYS[1]) ~= ARGV[1] then return 0 end " +
                    "redis.call('SET',KEYS[1],ARGV[2],'EX',ARGV[3]); return 1", Long.class);
    private final JwtUtil jwt;
    private final StringRedisTemplate redis;
    private final SysUserMapper users;

    public LoginVO create(SysUser user) {
        String sid = UUID.randomUUID().toString();
        String version = UUID.randomUUID().toString();
        try {
            redis.opsForValue().set(key(user.getId(), sid), value(user, version), Duration.ofSeconds(jwt.getRefreshTokenExpire()));
        } catch (Exception e) { throw unavailable(); }
        return pair(user, sid, version);
    }

    public SysUser authenticate(String token, String type) {
        Claims claims = claims(token, type);
        SysUser user = activeUser(Long.valueOf(claims.getSubject()));
        String actual;
        try { actual = redis.opsForValue().get(key(user.getId(), claims.get("sid", String.class))); }
        catch (Exception e) { throw unavailable(); }
        if (!value(user, claims.get("version", String.class)).equals(actual)) throw unauthorized();
        return user;
    }

    public LoginVO refresh(String token) {
        Claims claims = claims(token, "refresh");
        SysUser user = activeUser(Long.valueOf(claims.getSubject()));
        String version = UUID.randomUUID().toString();
        Long rotated;
        try {
            rotated = redis.execute(ROTATE, List.of(key(user.getId(), claims.get("sid", String.class))),
                    value(user, claims.get("version", String.class)), value(user, version), Long.toString(jwt.getRefreshTokenExpire()));
        } catch (Exception e) { throw unavailable(); }
        if (!Long.valueOf(1).equals(rotated)) throw unauthorized();
        return pair(user, claims.get("sid", String.class), version);
    }

    public void logout(String token) {
        Claims claims = claims(token, "access");
        try { redis.delete(key(Long.valueOf(claims.getSubject()), claims.get("sid", String.class))); }
        catch (Exception e) { throw unavailable(); }
    }

    public void revokeAll(Long userId) {
        String prefix="trip:auth:session:{"+userId+"}:";
        try(var cursor=redis.scan(ScanOptions.scanOptions().match(prefix+"*").count(100).build())) {
            while(cursor.hasNext()) {
                String key=cursor.next();
                if(!key.startsWith(prefix))throw new IllegalStateException("Unexpected session key");
                redis.delete(key);
            }
        } catch(Exception e) {throw unavailable();}
    }

    private SysUser activeUser(Long id) {
        SysUser user;
        try { user = users.selectById(id); }
        catch (Exception e) { throw unavailable(); }
        if (user == null || !Integer.valueOf(1).equals(user.getStatus()) || Integer.valueOf(1).equals(user.getDeleted())) throw unauthorized();
        return user;
    }

    private Claims claims(String token, String type) {
        try {
            Claims c = jwt.parse(token);
            if (!type.equals(c.get("type", String.class)) || Long.parseLong(c.getSubject()) <= 0) throw unauthorized();
            UUID.fromString(c.get("sid", String.class));
            UUID.fromString(c.get("version", String.class));
            return c;
        } catch (JwtException | IllegalArgumentException | NullPointerException e) { throw unauthorized(); }
    }

    private LoginVO pair(SysUser user, String sid, String version) {
        return LoginVO.builder().accessToken(jwt.createAccessToken(user.getId(), user.getRole(), sid, version))
                .refreshToken(jwt.createRefreshToken(user.getId(), user.getRole(), sid, version))
                .expiresIn(jwt.getAccessTokenExpire()).userInfo(UserVO.from(user)).build();
    }

    private String key(Long userId, String sid) { return "trip:auth:session:{" + userId + "}:" + sid; }

    // This fingerprint stays on the server. Changing the password invalidates every existing session.
    private String value(SysUser user, String version) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(user.getPassword().getBytes(StandardCharsets.UTF_8))) + ":" + version;
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private BizException unauthorized() { return new BizException(401, "未登录或登录已过期，请重新登录"); }
    private BizException unavailable() { return new BizException(503, "认证服务暂不可用，请稍后重试"); }
}
