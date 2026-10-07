package com.trip.module.analysis;

import com.trip.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/analysis")
public class AdminAnalysisController {
    private final AnalysisService service;

    @GetMapping("/dashboard")
    public R<AnalysisService.Dashboard> dashboard(@RequestParam(defaultValue="30") int days,
                                                  @RequestParam(required=false) Long destinationId) {
        return R.ok(service.dashboard(days, destinationId));
    }
}
