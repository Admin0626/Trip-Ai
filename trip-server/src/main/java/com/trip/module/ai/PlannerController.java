package com.trip.module.ai;

import com.trip.common.result.R;
import com.trip.common.exception.BizException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import tools.jackson.databind.annotation.JsonDeserialize;

@RestController
@RequestMapping("/ai/planner")
@RequiredArgsConstructor
public class PlannerController {
    private final PlannerService service;
    private final PlannerEndpointPolicy policy;
    private final AiQuotaService quota;
    private final PlannerCircuitService circuit;
    public record Test(@Valid @NotNull PlannerConnection connection) {}
    public record Generate(@Valid @NotNull PlannerConnection connection,
                           @NotBlank @Size(min=5,max=1000) @JsonDeserialize(using=StrictPlannerJson.Text.class) String query,
                           @NotNull @Min(1) @Max(14) @JsonDeserialize(using=StrictPlannerJson.IntegerNumber.class) Integer days,
                           @NotNull @DecimalMin("0.01") @DecimalMax("1000000") @JsonDeserialize(using=StrictPlannerJson.DecimalNumber.class) BigDecimal budget,
                           @NotNull @Min(1) @Max(10) @JsonDeserialize(using=StrictPlannerJson.IntegerNumber.class) Integer peopleNum,
                           @JsonDeserialize(using=StrictPlannerJson.Text.class) String startDate) {}
    @GetMapping("options") public R<PlannerEndpointPolicy.Options> options() { return R.ok(policy.options()); }
    @GetMapping("usage") public R<AiQuotaService.Usage> usage(@AuthenticationPrincipal Long userId) { return R.ok(quota.usage(userId)); }
    @PostMapping("circuit") public R<PlannerCircuitService.Snapshot> circuit(@AuthenticationPrincipal Long userId, @Valid @RequestBody Test request) {
        return R.ok(circuit.status(userId,policy.endpoint(request.connection().baseUrl()),request.connection()));
    }
    @PostMapping("test") public R<Object> test(@AuthenticationPrincipal Long userId, @Valid @RequestBody Test request) {
        return R.ok(service.execute(userId, new Generate(request.connection(), null, null, null, null, null), true));
    }
    @PostMapping("generate") public R<Object> generate(@AuthenticationPrincipal Long userId, @Valid @RequestBody Generate request) {
        if (request.query().strip().length() < 5) throw new BizException(400, "旅行需求至少5字");
        if (request.startDate() != null) {
            try { if (java.time.LocalDate.parse(request.startDate()).isBefore(java.time.LocalDate.now())) throw new IllegalArgumentException(); }
            catch (Exception e) { throw new BizException(400, "出发日期格式须为yyyy-MM-dd且为今天及以后"); }
        }
        return R.ok(service.execute(userId, request, false));
    }
}
