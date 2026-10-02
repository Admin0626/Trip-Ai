package com.trip.module.user.service;

import com.trip.common.exception.BizException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Purpose-scoped, single-use challenges; no plaintext codes or email addresses in Redis keys. */
@Service
public class EmailCodeService {
    public static final int TTL = 600;
    public static final int COOLDOWN = 60;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> ADMIT = new DefaultRedisScript<>("""
        if redis.call('EXISTS',KEYS[1]) == 1 then return 0 end
        for i=2,4 do
          if tonumber(redis.call('GET',KEYS[i]) or '0') >= tonumber(ARGV[i]) then return 0 end
        end
        redis.call('SET',KEYS[1],ARGV[1],'EX',60)
        for i=2,4 do
          local n=redis.call('INCR',KEYS[i]); if n == 1 then redis.call('EXPIRE',KEYS[i],3600) end
        end
        return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> READY = new DefaultRedisScript<>("""
        if redis.call('HGET',KEYS[1],'id') ~= ARGV[1] then return 0 end
        redis.call('HSET',KEYS[1],'ready','1'); redis.call('EXPIRE',KEYS[1],600); return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> DISCARD = new DefaultRedisScript<>("""
        if redis.call('HGET',KEYS[1],'id') == ARGV[1] then redis.call('DEL',KEYS[1]) end
        if redis.call('GET',KEYS[2]) == ARGV[1] then redis.call('DEL',KEYS[2]) end
        return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
        if redis.call('HGET',KEYS[1],'ready') ~= '1' then return 0 end
        if redis.call('HGET',KEYS[1],'id') == ARGV[1] and
           redis.call('HGET',KEYS[1],'digest') == ARGV[2] and
           redis.call('HGET',KEYS[1],'binding') == ARGV[3] then
          redis.call('DEL',KEYS[1]); return 1
        end
        local n=redis.call('HINCRBY',KEYS[1],'attempts',1)
        if n >= 5 then redis.call('DEL',KEYS[1]) end
        return 0
        """, Long.class);
    private final StringRedisTemplate redis;
    private final ObjectProvider<JavaMailSender> senders;
    private final boolean enabled;
    private final String from;

    public EmailCodeService(StringRedisTemplate redis, ObjectProvider<JavaMailSender> senders,
                            @Value("${trip.mail.enabled:false}") boolean enabled,
                            @Value("${trip.mail.from:}") String from) {
        this.redis=redis; this.senders=senders; this.enabled=enabled; this.from=from;
    }

    public static String email(Object value) {
        if (!(value instanceof String s)) throw new BizException(400,"邮箱必须为字符串");
        String email=s.trim().toLowerCase(Locale.ROOT);
        if (email.length()>100 || !email.matches("[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\\.[a-z]{2,63}"))
            throw new BizException(400,"邮箱格式不正确，最长100字符");
        return email;
    }

    public static String text(Map<String,Object> body,String field,int max) {
        if (!(body.get(field) instanceof String s) || s.isBlank() || s.length()>max)
            throw new BizException(400,field+"格式或长度不正确");
        return s;
    }

    public static void fields(Map<String,Object> body,String... fields) {
        if (!body.keySet().equals(Set.of(fields))) throw new BizException(400,"请求字段缺失或包含不支持的字段");
    }

    public static void password(String password) {
        if (password.length()<8 || password.length()>20 || password.chars().noneMatch(Character::isLetter)
                || password.chars().noneMatch(Character::isDigit))
            throw new BizException(400,"新密码须为8-20位并同时包含字母与数字");
    }

    public void available() {
        if (!enabled || from.isBlank() || senders.getIfAvailable()==null)
            throw new BizException(503,"邮件服务尚未配置，暂时无法发送验证码");
    }

    /** Called before password checks to limit attempts on authenticated binding as well. */
    public String reserve(String email,String ip) {
        available();
        String id=UUID.randomUUID().toString();
        try {
            Long ok=redis.execute(ADMIT,List.of(cooldown(email),"trip:mail:limit:email:"+hash(email),
                    "trip:mail:limit:ip:"+hash(ip),"trip:mail:limit:global"),id,"5","20","100");
            if (!Long.valueOf(1).equals(ok)) throw new BizException(429,"发送过于频繁，请稍后再试（至少间隔60秒）");
            return id;
        } catch(BizException e) { throw e; }
        catch(Exception e) { throw unavailable(); }
    }

    public void send(String purpose,String email,Long userId,String binding,String id) {
        String key=key(purpose,email,userId);
        String code=String.format(Locale.ROOT,"%06d",RANDOM.nextInt(1_000_000));
        try {
            redis.opsForHash().putAll(key,Map.of("id",id,"digest",hash(id+":"+code),"binding",binding,"ready","0","attempts","0"));
            redis.expire(key,java.time.Duration.ofSeconds(TTL));
            SimpleMailMessage message=new SimpleMailMessage();
            message.setFrom(from); message.setTo(email); message.setSubject("Trip-AI 邮箱验证码");
            String action=switch(purpose) { case "REGISTER" -> "注册并验证邮箱"; case "BIND" -> "验证或更换邮箱"; default -> "找回密码"; };
            // Same delivery and wording for existing and unknown recovery addresses.
            message.setText("您正在"+action+"。验证码："+code+"\n验证码10分钟内有效，仅可使用一次。\n找回密码仅适用于已验证邮箱的有效账号。\n如非本人操作，请忽略此邮件，不要向他人透露验证码。");
            senders.getObject().send(message);
            if (!Long.valueOf(1).equals(redis.execute(READY,List.of(key),id))) throw unavailable();
        } catch(Exception e) {
            try { redis.execute(DISCARD,List.of(key,cooldown(email)),id); } catch(Exception ignored) { }
            // Never log SMTP exceptions: providers may include recipient addresses and credentials.
            throw new BizException(503,"邮件发送失败，请稍后重新发送");
        }
    }

    public void consume(String purpose,String email,Long userId,String binding,String code) {
        if (code==null || !code.matches("[0-9]{6}")) throw invalid();
        String key=key(purpose,email,userId);
        try {
            Object id=redis.opsForHash().get(key,"id");
            if (id==null || !Long.valueOf(1).equals(redis.execute(CONSUME,List.of(key),id.toString(),hash(id+":"+code),binding)))
                throw invalid();
        } catch(BizException e) { throw e; }
        catch(Exception e) { throw unavailable(); }
    }

    public static String binding(com.trip.module.user.entity.SysUser user) {
        return hash(user.getId()+":"+user.getPassword()+":"+user.getEmail()+":"+user.getEmailVerified()+":"+user.getStatus());
    }
    static String key(String purpose,String email,Long userId) { return "trip:mail:code:"+purpose+":"+(userId==null?"public":userId)+":"+hash(email); }
    static String cooldown(String email) { return "trip:mail:cooldown:"+hash(email); }
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static BizException invalid() { return new BizException(400,"验证码无效或已过期，请重新获取"); }
    private static BizException unavailable() { return new BizException(503,"验证码服务暂不可用，请稍后重试"); }
}
