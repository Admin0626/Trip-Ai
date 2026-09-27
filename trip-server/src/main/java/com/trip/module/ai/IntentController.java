package com.trip.module.ai;

import com.trip.common.exception.BizException;
import com.trip.common.result.R;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/recommend")
@RequiredArgsConstructor
public class IntentController {
    private final IntentService service;

    // Object preserves the JSON type; Jackson otherwise coerces numbers into String.
    public record IntentRequest(@Schema(type = "string", minLength = 5, maxLength = 500) Object query) {}

    @PostMapping("/intent")
    public R<IntentResult> parse(@AuthenticationPrincipal Long userId, @RequestBody IntentRequest request) {
        if (!(request.query() instanceof String raw)) throw new BizException(400, "query 必须为字符串");
        String query = raw.strip();
        if (raw.length() > 500 || query.length() < 5) throw new BizException(400, "query 须为5—500字，首尾空白不计入最小长度");
        return R.ok(service.parse(userId, query));
    }
}
