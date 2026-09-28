package com.trip.module.ai;

import com.trip.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class PlannerCircuitService {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final String prefix;
    private final int window, cooldown, minimum, threshold, lease;
    private final DefaultRedisScript<String> script=new DefaultRedisScript<>();
    public PlannerCircuitService(StringRedisTemplate redis, ObjectMapper json,
            @Value("${trip.ai.circuit.key-prefix:trip:ai:circuit}") String prefix,
            @Value("${trip.ai.circuit.window-seconds:300}") int window,
            @Value("${trip.ai.circuit.cooldown-seconds:600}") int cooldown,
            @Value("${trip.ai.circuit.minimum-calls:10}") int minimum,
            @Value("${trip.ai.circuit.failure-percent:30}") int threshold,
            @Value("${trip.ai.circuit.probe-lease-seconds:90}") int lease,
            @Value("${trip.ai.timeout-seconds:45}") int timeout) {
        if(!prefix.matches("[a-zA-Z0-9:_-]{1,80}") || window<1 || window>3600 || cooldown<1 || cooldown>86400
                || minimum<1 || minimum>10000 || threshold<1 || threshold>99 || lease<Math.min(60,Math.max(1,timeout))+1 || lease>300)
            throw new IllegalArgumentException("Invalid AI circuit configuration");
        this.redis=redis; this.json=json; this.prefix=prefix; this.window=window; this.cooldown=cooldown;
        this.minimum=minimum; this.threshold=threshold; this.lease=lease;
        script.setLocation(new ClassPathResource("redis/ai_circuit.lua")); script.setResultType(String.class);
    }
    // Internal permit is never sent to the client or persisted to SQL.
    public record Permit(List<String> keys, String ticket, String generation) {}
    public record Snapshot(String phase, long total, long failed, double failurePercent, int minimumCalls,
                           int windowSeconds, int cooldownSeconds, String retryAt, long retryAfterSeconds) {}
    static List<String> keys(String prefix,long userId,URI endpoint,PlannerConnection connection) {
        try {
            // NUL separators are unambiguous with validated URL/model/printable key.
            String identity=endpoint.toASCIIString()+"\0"+connection.model()+"\0"+(connection.apiKey()==null?"":connection.apiKey().strip());
            String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));
            String base=prefix+":{"+userId+":"+digest+"}:";
            return List.of(base+"state",base+"events",base+"failures");
        } catch(Exception e) { throw new IllegalStateException("Circuit identity unavailable"); }
    }
    private tools.jackson.databind.JsonNode invoke(List<String> keys,String action,String ticket,String generation,boolean success) {
        try {
            String raw=redis.execute(script,keys,action,ticket,generation,success?"1":"0",Integer.toString(window*1000),
                    Integer.toString(cooldown*1000),Integer.toString(minimum),Integer.toString(threshold),Integer.toString(lease*1000),
                    Long.toString(2L*(window+cooldown+lease)*1000));
            var result=json.readTree(raw);
            if(result==null || !Set.of("CLOSED","OPEN","HALF_OPEN").contains(result.path("phase").asString())
                    || !result.has("total") || !result.has("generation")) throw new IllegalStateException();
            return result;
        } catch(Exception e) { throw new BizException(503,"模型状态服务暂不可用，请稍后重试或使用基础旅行推荐"); }
    }
    public Permit acquire(long userId,URI endpoint,PlannerConnection connection) {
        var keys=keys(prefix,userId,endpoint,connection); String ticket=UUID.randomUUID().toString();
        var r=invoke(keys,"acquire",ticket,"",false);
        if(!r.path("allowed").asBoolean()) {
            String message=r.path("phase").asString().equals("HALF_OPEN")?"模型服务正在恢复探测，请稍后重试":
                    "模型服务因连续故障暂时暂停，请在约"+r.path("retryAfterSeconds").asLong()+"秒后重试";
            throw new BizException(503,message+"，也可使用基础旅行推荐");
        }
        return new Permit(keys,ticket,r.path("generation").asString());
    }
    public void complete(Permit permit,boolean success) { invoke(permit.keys(),"complete",permit.ticket(),permit.generation(),success); }
    public void abandon(Permit permit) { invoke(permit.keys(),"abandon",permit.ticket(),permit.generation(),false); }
    public Snapshot status(long userId,URI endpoint,PlannerConnection connection) {
        var r=invoke(keys(prefix,userId,endpoint,connection),"status",UUID.randomUUID().toString(),"",false);
        long total=r.path("total").asLong(),failed=r.path("failed").asLong(),retry=r.path("retryAt").asLong();
        return new Snapshot(r.path("phase").asString(),total,failed,total==0?0:failed*100.0/total,
                minimum,window,cooldown,retry==0?null:Instant.ofEpochMilli(retry).toString(),r.path("retryAfterSeconds").asLong());
    }
}
