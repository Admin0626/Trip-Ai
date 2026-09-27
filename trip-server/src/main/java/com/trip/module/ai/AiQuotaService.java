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
    record Windows(List<String> keys, ZonedDateTime hourReset, ZonedDateTime dayReset) {}
    public record Bucket(long limit, long used, long remaining, String resetAt) {}
    public record Quota(Bucket hourly, Bucket daily, Bucket globalDaily, String timeZone) {}
    public record Statistics(long operations, long succeeded, long failed, double averageCostMs) {}
    public record Usage(Quota quota, Statistics today) {}

    static Windows windows(long userId, String prefix, Instant now) {
        var local = now.atZone(ZONE);
        var hour = local.truncatedTo(ChronoUnit.HOURS);
        var day = local.toLocalDate().atStartOfDay(ZONE);
        String base = prefix + ":{ai-quota}:";
        return new Windows(List.of(base + "user:" + userId + ":hour:" + hour.toEpochSecond(),
                base + "user:" + userId + ":day:" + day.toEpochSecond(), base + "global:day:" + day.toEpochSecond()), hour.plusHours(1), day.plusDays(1));
    }
    private int[] limits() {
        try {
            var rows = jdbc.queryForList("SELECT config_key,config_value FROM recommend_config WHERE config_key IN ('llm.quota.user.hourly','llm.quota.user.daily','llm.daily.quota')");
            Map<String, String> values = new HashMap<>();
            for (var row : rows) values.put(row.get("config_key").toString(), row.get("config_value").toString());
            int[] result = {Integer.parseInt(values.getOrDefault("llm.quota.user.hourly", "20")),
                    Integer.parseInt(values.getOrDefault("llm.quota.user.daily", "200")), Integer.parseInt(values.getOrDefault("llm.daily.quota", "2000"))};
            for (int value : result) if (value < 1 || value > 10000000) throw new IllegalArgumentException();
            return result;
        } catch (Exception e) { throw new BizException(503, "AI配额配置暂不可用，请稍后重试"); }
    }
    public void acquire(long userId) {
        int[] limit = limits();
        Windows window = windows(userId, prefix, Instant.now());
        List<?> result;
        try {
            result = redis.execute(script, window.keys(), Integer.toString(limit[0]), Integer.toString(limit[1]), Integer.toString(limit[2]),
                    Long.toString(window.hourReset().toEpochSecond()), Long.toString(window.dayReset().toEpochSecond()), Long.toString(window.dayReset().toEpochSecond()));
            if (result == null || result.size() != 5) throw new IllegalStateException();
        } catch (Exception e) { throw new BizException(503, "AI配额服务暂不可用，未发出新的模型请求，请稍后重试"); }
        if (((Number)result.get(0)).intValue() == 0) {
            String scope = switch (((Number)result.get(1)).intValue()) { case 1 -> "本小时"; case 2 -> "今日"; default -> "全站今日"; };
            throw new BizException(429, scope + "AI调用额度已用尽，请在额度重置后重试");
        }
    }
    public Usage usage(long userId) {
        int[] limit = limits();
        Windows window = windows(userId, prefix, Instant.now());
        long[] counts = new long[3];
        try {
            List<String> raw = redis.opsForValue().multiGet(window.keys());
            if (raw == null || raw.size() != 3) throw new IllegalStateException();
            for (int i=0; i<3; i++) {
                counts[i] = raw.get(i) == null ? 0 : Long.parseLong(raw.get(i));
                if (counts[i] < 0) throw new IllegalStateException();
            }
        } catch (Exception e) { throw new BizException(503, "AI配额服务暂不可用，请稍后刷新"); }
        var stats = jdbc.queryForObject("""
                SELECT COUNT(*) AS operations, COALESCE(SUM(success=1),0) AS succeeded,
                COALESCE(SUM(success=0),0) AS failed, COALESCE(AVG(cost_ms),0) AS average_cost
                FROM llm_call_log WHERE user_id=? AND scene IN ('USER_PLANNER','USER_MODEL_TEST') AND create_time>=?
                """, (rs, row) -> new Statistics(rs.getLong("operations"), rs.getLong("succeeded"), rs.getLong("failed"), rs.getDouble("average_cost")),
                userId, LocalDate.now(ZONE).atStartOfDay());
        return new Usage(new Quota(bucket(limit[0], counts[0], window.hourReset()), bucket(limit[1], counts[1], window.dayReset()),
                bucket(limit[2], counts[2], window.dayReset()), ZONE.getId()), stats);
    }
    private Bucket bucket(long limit, long used, ZonedDateTime reset) { return new Bucket(limit, used, Math.max(0, limit-used), reset.toOffsetDateTime().toString()); }
}
