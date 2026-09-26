package com.trip.module.plan.controller;

import com.trip.common.exception.BizException;
import com.trip.common.result.PageResult;
import com.trip.common.result.R;
import com.trip.common.result.ResultCode;
import com.trip.module.plan.dto.PlanSaveDTO;
import com.trip.module.plan.service.PlanService;
import com.trip.module.plan.vo.PlanVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自主规划接口（api/03 · /plan/**，均需登录 USER）。
 */
@RestController
@RequestMapping("/plan")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    /** POST /plan 创建规划 */
    @PostMapping
    public R<Long> create(@Valid @RequestBody PlanSaveDTO dto) {
        return R.ok(planService.create(currentUserId(), dto));
    }

    /** PUT /plan/{id} 更新规划（整体替换 dayList） */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody PlanSaveDTO dto) {
        planService.update(currentUserId(), id, dto);
        return R.ok();
    }

    /** GET /plan/page 我的规划分页 */
    @GetMapping("/page")
    public R<PageResult<PlanVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer status) {
        if (current < 1 || size < 1) throw new com.trip.common.exception.BizException(400, "分页参数须大于0");
        size = Math.min(size, 100);
        return R.ok(planService.page(currentUserId(), current, size, status));
    }

    /** GET /plan/{id} 规划详情 */
    @GetMapping("/{id}")
    public R<PlanVO> detail(@PathVariable Long id) {
        return R.ok(planService.detail(currentUserId(), id));
    }

    /** DELETE /plan/{id} 删除规划 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        planService.delete(currentUserId(), id);
        return R.ok();
    }

    /** POST /plan/{id}/copy 复制为新规划 */
    @PostMapping("/{id}/copy")
    public R<Long> copy(@PathVariable Long id) {
        return R.ok(planService.copy(currentUserId(), id));
    }

    /** POST /plan/{id}/export 导出文字版行程单 */
    @PostMapping("/{id}/export")
    public R<String> export(@PathVariable Long id) {
        return R.ok(planService.export(currentUserId(), id));
    }

    /** POST /plan/from-route/{routeId} 以系统路线为模板生成规划 */
    @PostMapping("/from-route/{routeId}")
    public R<Long> fromRoute(@PathVariable Long routeId) {
        return R.ok(planService.fromRoute(currentUserId(), routeId));
    }

    /** POST /plan/{id}/ai-optimize AI 优化（第 3 批开放，本批预留） */
    @PostMapping("/{id}/ai-optimize")
    public R<Void> aiOptimize(@PathVariable Long id) {
        planService.aiOptimize(currentUserId(), id);
        return R.ok();
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long)) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return (Long) auth.getPrincipal();
    }
}