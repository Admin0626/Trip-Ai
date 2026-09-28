package com.trip.module.ai;

import com.trip.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlannerCircuitTest {
    @Test void identityIsIsolatedAndNeverContainsSecrets() {
        var uri=URI.create("http://127.0.0.1:11435/v1/chat/completions");
        var connection=new PlannerConnection("unused","model","key");
        var a=PlannerCircuitService.keys("test",1,uri,connection);
        assertTrue(a.stream().noneMatch(k->k.contains("model")||k.contains("key")||k.contains("127.0.0.1")));
        assertNotEquals(a,PlannerCircuitService.keys("test",2,uri,connection));
        assertNotEquals(a,PlannerCircuitService.keys("test",1,URI.create("http://127.0.0.1:11436/v1/chat/completions"),connection));
        assertNotEquals(a,PlannerCircuitService.keys("test",1,uri,new PlannerConnection("unused","other","key")));
        assertNotEquals(a,PlannerCircuitService.keys("test",1,uri,new PlannerConnection("unused","model","new-key")));
        assertEquals(a,PlannerCircuitService.keys("test",1,uri,new PlannerConnection("unused","model"," key ")));
        assertEquals(a.get(0).split("}")[0],a.get(2).split("}")[0]);
    }
    @Test void rejectLeaseShorterThanBoundedRequestAndUnsafePrefix() {
        assertThrows(IllegalArgumentException.class,()->new PlannerCircuitService(null,null,"test",300,600,10,30,45,45));
        assertThrows(IllegalArgumentException.class,()->new PlannerCircuitService(null,null,"test{bad}",300,600,10,30,90,45));
    }
    @Test void unavailableRedisIsSafeAndCannotIssuePermit() {
        var redis=mock(StringRedisTemplate.class,invocation->{
            if(invocation.getMethod().getName().equals("execute")) throw new org.springframework.data.redis.RedisConnectionFailureException("secret internal endpoint");
            return RETURNS_DEFAULTS.answer(invocation);
        });
        var circuit=new PlannerCircuitService(redis,new ObjectMapper(),"test",300,600,10,30,90,45);
        var error=assertThrows(BizException.class,()->circuit.acquire(1,URI.create("http://127.0.0.1:11435/v1/chat/completions"),new PlannerConnection("unused","model","key")));
        assertEquals(503,error.getCode()); assertFalse(error.getMessage().contains("secret"));
    }
}
