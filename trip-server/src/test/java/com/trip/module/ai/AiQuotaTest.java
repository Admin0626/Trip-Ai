package com.trip.module.ai;

import com.trip.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiQuotaTest {
    @Test void shanghaiMidnightChangesAllWindowsAndUsersShareOnlyGlobalKey() {
        var before = AiQuotaService.windows(1, "test", Instant.parse("2026-09-27T15:59:59Z"));
        var after = AiQuotaService.windows(1, "test", Instant.parse("2026-09-27T16:00:00Z"));
        var other = AiQuotaService.windows(2, "test", Instant.parse("2026-09-27T16:00:00Z"));
        assertEquals("2026-09-28T00:00+08:00[Asia/Shanghai]", before.dayReset().toString());
        assertEquals(before.dayReset(), before.hourReset());
        for (int i=0;i<3;i++) assertNotEquals(before.keys().get(i), after.keys().get(i));
        assertNotEquals(after.keys().get(0), other.keys().get(0));
        assertNotEquals(after.keys().get(1), other.keys().get(1));
        assertEquals(after.keys().get(2), other.keys().get(2));
        assertTrue(after.keys().stream().allMatch(k -> k.contains("{ai-quota}")));
    }
    @Test void hourlyBoundaryKeepsDailyUsage() {
        var before = AiQuotaService.windows(1, "test", Instant.parse("2026-09-27T09:59:59Z"));
        var after = AiQuotaService.windows(1, "test", Instant.parse("2026-09-27T10:00:00Z"));
        assertNotEquals(before.keys().get(0), after.keys().get(0));
        assertEquals(before.keys().subList(1,3), after.keys().subList(1,3));
    }
    @Test void invalidConfigurationFailsBeforeRedis() {
        var redis=mock(StringRedisTemplate.class); var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("config_key","llm.quota.user.hourly","config_value","0")));
        var error=assertThrows(BizException.class, () -> new AiQuotaService(redis,jdbc,"test").acquire(1));
        assertEquals(503,error.getCode()); verifyNoInteractions(redis);
    }
    @Test void missingRedisResultFailsClosed() {
        var redis=mock(StringRedisTemplate.class); var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        assertEquals(503,assertThrows(BizException.class,()->new AiQuotaService(redis,jdbc,"test").acquire(1)).getCode());
    }
    @Test void redisConnectionFailureIsSafe503() {
        var redis=mock(StringRedisTemplate.class, invocation -> {
            if (invocation.getMethod().getName().equals("execute")) throw new org.springframework.data.redis.RedisConnectionFailureException("internal endpoint");
            return RETURNS_DEFAULTS.answer(invocation);
        });
        var jdbc=mock(JdbcTemplate.class); when(jdbc.queryForList(anyString())).thenReturn(List.of());
        var error=assertThrows(BizException.class,()->new AiQuotaService(redis,jdbc,"test").acquire(1));
        assertEquals(503,error.getCode()); assertFalse(error.getMessage().contains("internal endpoint"));
    }
    @Test void quotaFailurePreventsProviderAndPhantomAuditAndReleasesCapacity() {
        var quota=mock(AiQuotaService.class); var client=mock(CompatiblePlannerClient.class); var jdbc=mock(JdbcTemplate.class);
        doThrow(new BizException(503,"Redis unavailable")).when(quota).acquire(1);
        var planner=new PlannerService(new PlannerEndpointPolicy("",true),client,new PlannerOutputValidator(new ObjectMapper()),jdbc,new ObjectMapper(),quota);
        var request=new PlannerController.Generate(new PlannerConnection("http://localhost:11435/v1","fixture",""),null,null,null,null,null);
        for(int i=0;i<2;i++) assertEquals(503,assertThrows(BizException.class,()->planner.execute(1,request,true)).getCode());
        verify(quota,times(2)).acquire(1); verifyNoInteractions(client,jdbc);
    }
}
