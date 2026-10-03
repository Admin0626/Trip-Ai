package com.trip.module.ai;

import com.trip.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AiQuotaService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;
    private final String prefix;
    private final DefaultRedisScript<List> script = new DefaultRedisScript<>();
    public AiQuotaService(StringRedisTemplate redis, JdbcTemplate jdbc,
                          @Value("${trip.ai.quota.key-prefix:trip:ai:quota}") String prefix) {
        if (!prefix.matches("[a-zA-Z0-9:_-]{1,80}")) throw new IllegalArgumentException("Invalid quota key prefix");
        this.redis=redis; this.jdbc=jdbc; this.prefix=prefix;
        script.setLocation(new ClassPathResource("redis/ai_quota.lua")); script.setResultType(List.class);
    }
    record Windows(List<String> keys, ZonedDateTime hourReset) {}
    public record Bucket(Long limit, Long used, Long remaining, String resetAt, boolean enabled) {}
    public record Quota(Bucket hourly, Bucket daily, Bucket globalDaily, String timeZone) {}
    public record Statistics(long operations, long succeeded, long failed, double averageCostMs) {}
    public record Usage(Quota quota, Statistics today) {}

    static Windows windows(long userId, String prefix, Instant now) {
        var local = now.atZone(ZONE);
        var hour = local.truncatedTo(ChronoUnit.HOURS);
        String base = prefix + ":{ai-quota}:";
        return new Windows(List.of(base + "user:" + userId + ":hour:" + hour.toEpochSecond()), hour.plusHours(1));
    }
    private int hourlyLimit() {
        try {
            var rows = jdbc.queryForList("SELECT config_key,config_value FROM recommend_config WHERE config_key='llm.quota.user.hourly'");
            Map<String, String> values = new HashMap<>();
            for (var row : rows) values.put(row.get("config_key").toString(), row.get("config_value").toString());
            int result = Integer.parseInt(values.getOrDefault("llm.quota.user.hourly", "20"));
            if (result < 1 || result > 10000000) throw new IllegalArgumentException();
            return result;
        } catch (Exception e) { throw new BizException(503, "AI配额配置暂不可用，请稍后重试"); }
    }
    public void acquire(long userId) {
        int limit = hourlyLimit();
        Windows window = windows(userId, prefix, Instant.now());
        List<?> result;
        try {
            result = redis.execute(script, window.keys(), Integer.toString(limit), Long.toString(window.hourReset().toEpochSecond()));
            if (result == null || result.size() != 2) throw new IllegalStateException();
        } catch (Exception e) { throw new BizException(503, "AI配额服务暂不可用，未发出新的模型请求，请稍后重试"); }
        if (((Number)result.get(0)).intValue() == 0) {
            throw new BizException(429, "本小时AI调用次数已达上限，请在小时窗口重置后重试");
        }
    }
    public Usage usage(long userId) {
        int limit = hourlyLimit();
        Windows window = windows(userId, prefix, Instant.now());
        long used;
        try {
            List<String> raw = redis.opsForValue().multiGet(window.keys());
            if (raw == null || raw.size() != 1) throw new IllegalStateException();
            used = raw.get(0) == null ? 0 : Long.parseLong(raw.get(0));
            if (used < 0) throw new IllegalStateException();
        } catch (Exception e) { throw new BizException(503, "AI配额服务暂不可用，请稍后刷新"); }
        var stats = jdbc.queryForObject("""
                SELECT COUNT(*) AS operations, COALESCE(SUM(success=1),0) AS succeeded,
                COALESCE(SUM(success=0),0) AS failed, COALESCE(AVG(cost_ms),0) AS average_cost
                FROM llm_call_log WHERE user_id=? AND scene IN ('USER_PLANNER','USER_MODEL_TEST') AND create_time>=?
                """, (rs, row) -> new Statistics(rs.getLong("operations"), rs.getLong("succeeded"), rs.getLong("failed"), rs.getDouble("average_cost")),
                userId, LocalDate.now(ZONE).atStartOfDay());
        // Keep the old fields for clients inspecting them; null means no daily cap, not zero remaining.
        var unlimited = new Bucket(null, null, null, null, false);
        return new Usage(new Quota(bucket(limit, used, window.hourReset()), unlimited, unlimited, ZONE.getId()), stats);
    }
    private Bucket bucket(long limit, long used, ZonedDateTime reset) { return new Bucket(limit, used, Math.max(0, limit-used), reset.toOffsetDateTime().toString(), true); }
}
