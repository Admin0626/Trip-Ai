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
    @Test void shanghaiMidnightResetsHourAndUsersHaveSeparateCounters() {
        var before = AiQuotaService.windows(1, "test", Instant.parse("2026-09-27T15:59:59Z"));
        var after = AiQuotaService.windows(1, "test", Instant.parse("2026-09-27T16:00:00Z"));
        var other = AiQuotaService.windows(2, "test", Instant.parse("2026-09-27T16:00:00Z"));
        assertEquals("2026-09-28T00:00+08:00[Asia/Shanghai]", before.hourReset().toString());
        assertEquals(1, after.keys().size());
        assertNotEquals(before.keys(), after.keys());
        assertNotEquals(after.keys().get(0), other.keys().get(0));
        assertTrue(after.keys().stream().allMatch(k -> k.contains("{ai-quota}")));
    }
    @Test void hourlyBoundaryStartsANewCounter() {
        var before = AiQuotaService.windows(1, "test", Instant.parse("2026-09-27T09:59:59Z"));
        var after = AiQuotaService.windows(1, "test", Instant.parse("2026-09-27T10:00:00Z"));
        assertNotEquals(before.keys().get(0), after.keys().get(0));
        assertEquals(1, after.keys().size());
    }
    @Test void retiredDailyConfigurationCannotRejectCalls() {
        var redis=mock(StringRedisTemplate.class, invocation -> {
            if (invocation.getMethod().getName().equals("execute")) {
                List<?> keys = invocation.getArgument(1);
                assertEquals(1, keys.size());
                assertTrue(keys.get(0).toString().contains(":hour:"));
                return List.of(1L, 1L);
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
            Map.of("config_key","llm.quota.user.daily","config_value","0"),
            Map.of("config_key","llm.daily.quota","config_value","invalid")));
        assertDoesNotThrow(() -> new AiQuotaService(redis,jdbc,"test").acquire(1));
        verify(jdbc).queryForList(argThat(sql -> !sql.contains("llm.quota.user.daily") && !sql.contains("llm.daily.quota")));
    }
    @Test void exhaustedHourlyLimitStillRejects() {
        var redis=mock(StringRedisTemplate.class, invocation -> invocation.getMethod().getName().equals("execute")
            ? List.of(0L, 20L) : RETURNS_DEFAULTS.answer(invocation));
        var jdbc=mock(JdbcTemplate.class); when(jdbc.queryForList(anyString())).thenReturn(List.of());
        var error=assertThrows(BizException.class,()->new AiQuotaService(redis,jdbc,"test").acquire(1));
        assertEquals(429,error.getCode()); assertTrue(error.getMessage().contains("本小时"));
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
        var circuit=mock(PlannerCircuitService.class);
        var permit=new PlannerCircuitService.Permit(List.of("fixture"),"ticket","generation");
        when(circuit.acquire(eq(1L),any(),any())).thenReturn(permit);
        var planner=new PlannerService(new PlannerEndpointPolicy("",true),client,new PlannerOutputValidator(new ObjectMapper()),jdbc,new ObjectMapper(),quota,circuit);
        var request=new PlannerController.Generate(new PlannerConnection("http://localhost:11435/v1","fixture",""),null,null,null,null,null);
        for(int i=0;i<2;i++) assertEquals(503,assertThrows(BizException.class,()->planner.execute(1,request,true)).getCode());
        verify(quota,times(2)).acquire(1); verifyNoInteractions(client,jdbc);
        verify(circuit,times(2)).abandon(permit); verify(circuit,never()).complete(any(),anyBoolean());
    }
}
