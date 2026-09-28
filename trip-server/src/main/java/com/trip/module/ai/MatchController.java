package com.trip.module.ai;

import com.trip.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.Map;

@RestController
@RequestMapping("/ai/recommend")
@RequiredArgsConstructor
public class MatchController {
    private final PreferenceMatchService service;
    public record Request(Map<String, Object> intent, Object topN, Object useSavedPreference) {}

    @PostMapping("/match")
    public R<MatchService.Result> match(@AuthenticationPrincipal Long userId,@RequestBody Request request) {
        return R.ok(service.match(userId,request));
    }
}
