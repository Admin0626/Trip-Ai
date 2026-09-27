package com.trip.module.ai;

import com.trip.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class PlannerService {
    private final PlannerEndpointPolicy policy;
    private final CompatiblePlannerClient client;
    private final PlannerOutputValidator validator;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Set<Long> running = ConcurrentHashMap.newKeySet();
    private final Semaphore capacity = new Semaphore(8);
    public record Preview(PlannerOutputValidator.Draft draft, String source, String model, int attempts) {}

    public Object execute(long userId, PlannerController.Generate request, boolean test) {
        var endpoint = policy.endpoint(request.connection().baseUrl());
        if (!running.add(userId)) throw new BizException(429, "你已有一个AI请求正在处理，请稍后再试");
        if (!capacity.tryAcquire()) { running.remove(userId); throw new BizException(429, "AI服务繁忙，请稍后再试"); }
        long start = System.nanoTime();
        boolean success = false;
        String error = "FAILED";
        try {
            if (test) {
                client.call(endpoint, request.connection(), "Reply with OK only.", "Connection test", false);
                success = true;
                return Map.of("connected", true, "model", request.connection().model());
            }
            var templates = jdbc.queryForList("SELECT content FROM prompt_template WHERE code='USER_PLANNER' AND status=1 ORDER BY version DESC LIMIT 1", String.class);
            if (templates.isEmpty()) throw new BizException(3004, "AI规划模板尚未启用，请联系部署者完成初始化");
            String prompt = templates.get(0).replace("{{days}}", String.valueOf(request.days()));
            String user = json.writeValueAsString(Map.of("query", request.query().strip(), "days", request.days(), "budget", request.budget(), "peopleNum", request.peopleNum(),
                    "startDate", request.startDate() == null ? java.time.LocalDate.now().toString() : request.startDate()));
            for (int attempt = 1; attempt <= 2; attempt++) {
                String text = client.call(endpoint, request.connection(), prompt, user + (attempt == 2 ? "\n上次输出结构无效，请严格按要求重新输出完整JSON。" : ""), true);
                try {
                    var draft = validator.validate(text, request.days());
                    success = true;
                    return new Preview(draft, "USER_MODEL", request.connection().model(), attempt);
                } catch (IllegalArgumentException e) { if (attempt == 2) throw new BizException(3004, "模型两次返回的行程结构均不合格，请换用支持JSON输出的模型或减少天数"); }
            }
            throw new IllegalStateException();
        } catch (BizException e) { error = "BUSINESS_" + e.getCode(); throw e; }
        finally {
            running.remove(userId); capacity.release();
            // No endpoint, key, prompt or provider response is persisted in this audit row.
            jdbc.update("INSERT INTO llm_call_log(user_id,scene,model,cost_ms,success,is_fallback,error_msg) VALUES (?,?,?,?,?,0,?)",
                    userId, test ? "USER_MODEL_TEST" : "USER_PLANNER", request.connection().model(),
                    (System.nanoTime()-start)/1000000, success ? 1 : 0, success ? "" : error);
        }
    }
}
