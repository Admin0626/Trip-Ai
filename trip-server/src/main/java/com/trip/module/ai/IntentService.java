package com.trip.module.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IntentService {
    private final JdbcTemplate jdbc;
    private final RuleIntentParser parser;

    public IntentResult parse(long userId, String query) {
        long start = System.nanoTime();
        var names = jdbc.queryForList("""
                SELECT name FROM destination WHERE status=1 AND deleted=0
                UNION SELECT province FROM destination WHERE status=1 AND deleted=0
                UNION SELECT city FROM destination WHERE status=1 AND deleted=0
                """, String.class);
        var result = parser.parse(query, names);
        // success describes the model call, not the HTTP response. No model was called.
        jdbc.update("""
                INSERT INTO llm_call_log(user_id,scene,model,cost_ms,success,is_fallback,error_msg)
                VALUES (?, 'INTENT_PARSE', 'rule-only', ?, 0, 1, 'MODEL_DISABLED')
                """, userId, (System.nanoTime() - start) / 1_000_000);
        return result;
    }
}
