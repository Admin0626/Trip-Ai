package com.trip.module.ai;

import com.trip.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/ai/recommend")
@RequiredArgsConstructor
public class MatchController {
    private final MatchService service;
    public record Request(Map<String, Object> intent, Object topN) {}

    @PostMapping("/match")
    public R<MatchService.Result> match(@RequestBody Request request) {
        return R.ok(service.match(MatchCriteria.from(request.intent(), request.topN())));
    }
}
