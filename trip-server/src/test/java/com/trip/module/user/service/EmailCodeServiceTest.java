package com.trip.module.user.service;

import com.trip.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailCodeServiceTest {
    @Test void normalizesWithoutAliasing() {
        assertEquals("person+trip@example.com",EmailCodeService.email(" Person+Trip@Example.COM "));
        assertNotEquals(EmailCodeService.hash("person@example.com"),EmailCodeService.hash("person+trip@example.com"));
    }
    @Test void rejectsInjectionAndNonString() {
        for(Object value:new Object[]{12,"a@example.com\r\nBcc:x@example.com","a@x","x y@example.com","x".repeat(101)+"@example.com"})
            assertThrows(BizException.class,()->EmailCodeService.email(value));
    }
    @Test void validatesWithoutTrimmingSecrets() {
        EmailCodeService.password("Valid1234");
        for(String value:new String[]{"abc123","abcdefgh","12345678","a1".repeat(11)})
            assertThrows(BizException.class,()->EmailCodeService.password(value));
        assertEquals(" pass123 ",EmailCodeService.text(Map.of("password"," pass123 "),"password",20));
    }
    @Test void strictFieldsAndTypes() {
        assertThrows(BizException.class,()->EmailCodeService.fields(Map.of("email","x@example.com","code",123456),"email"));
        assertThrows(BizException.class,()->EmailCodeService.text(Map.of("code",123456),"code",6));
    }
    @Test void disabledNeverTouchesRedis() {
        var redis=mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> senders=mock(ObjectProvider.class);
        var codes=new EmailCodeService(redis,senders,false,"");
        assertEquals(503,assertThrows(BizException.class,()->codes.reserve("x@example.com","127.0.0.1")).getCode());
        verifyNoInteractions(redis,senders);
    }
    @Test void isolatesChallengeKeys() {
        assertNotEquals(EmailCodeService.key("BIND","x@example.com",1L),EmailCodeService.key("BIND","x@example.com",2L));
        assertNotEquals(EmailCodeService.key("RESET","x@example.com",null),EmailCodeService.key("REGISTER","x@example.com",null));
        assertFalse(EmailCodeService.key("RESET","x@example.com",null).contains("x@example.com"));
    }
    @Test void invalidatesOnAccountChanges() {
        var user=new com.trip.module.user.entity.SysUser();user.setId(1L);user.setPassword("hash1");user.setEmail("x@example.com");user.setEmailVerified(1);user.setStatus(1);
        String initial=EmailCodeService.binding(user);
        user.setPassword("hash2");assertNotEquals(initial,EmailCodeService.binding(user));user.setPassword("hash1");
        user.setEmail("y@example.com");assertNotEquals(initial,EmailCodeService.binding(user));user.setEmail("x@example.com");
        user.setEmailVerified(0);assertNotEquals(initial,EmailCodeService.binding(user));user.setEmailVerified(1);
        user.setStatus(0);assertNotEquals(initial,EmailCodeService.binding(user));
    }
}
