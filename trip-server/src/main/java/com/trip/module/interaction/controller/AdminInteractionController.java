package com.trip.module.interaction.controller;

import com.trip.common.result.PageResult;
import com.trip.common.result.R;
import com.trip.module.interaction.dto.BookingStatusDTO;
import com.trip.module.interaction.service.InteractionService;
import com.trip.module.interaction.vo.BookingVO;
import com.trip.module.interaction.vo.CommentVO;
import com.trip.module.interaction.vo.RouteStatVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 互动管理端接口（api/03 · /admin/interaction/**，ADMIN，Security 已按 /admin/** 拦截）
 */
@RestController
@RequestMapping("/admin/interaction")
@RequiredArgsConstructor
public class AdminInteractionController {

    private final InteractionService interactionService;

    /** GET /admin/interaction/booking/page 全部预约（分页，可按状态/路线筛） */
    @GetMapping("/booking/page")
    public R<PageResult<BookingVO>> bookingPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long routeId) {
        if (current < 1 || size < 1) throw new com.trip.common.exception.BizException(400, "分页参数须大于0");
        size = Math.min(size, 100);
        return R.ok(interactionService.adminBookingPage(current, size, status, routeId));
    }

    /** PUT /admin/interaction/booking/{id}/status 审核预约状态 */
    @PutMapping("/booking/{id}/status")
    public R<Void> updateBookingStatus(@PathVariable Long id,
                                       @Valid @RequestBody BookingStatusDTO dto) {
        interactionService.adminUpdateBookingStatus(currentAdminId(), id, dto.getStatus());
        return R.ok();
    }

    /** GET /admin/interaction/comment/page 评论管理分页 */
    @GetMapping("/comment/page")
    public R<PageResult<CommentVO>> commentPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long routeId) {
        if (current < 1 || size < 1) throw new com.trip.common.exception.BizException(400, "分页参数须大于0");
        size = Math.min(size, 100);
        return R.ok(interactionService.adminCommentPage(current, size, status, routeId));
    }

    /** PUT /admin/interaction/comment/{id}/status 显示 / 隐藏评论 */
    @PutMapping("/comment/{id}/status")
    public R<Void> hideComment(@PathVariable Long id,
                               @Valid @RequestBody com.trip.module.interaction.dto.CommentStatusDTO dto) {
        interactionService.adminHideComment(currentAdminId(), id, dto.getStatus());
        return R.ok();
    }

    /** GET /admin/interaction/stat/{routeId} 单条路线互动统计 */
    @GetMapping("/stat/{routeId}")
    public R<RouteStatVO> stat(@PathVariable Long routeId) {
        return R.ok(interactionService.adminStat(routeId));
    }

    private Long currentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long)) {
            return 0L;
        }
        return (Long) auth.getPrincipal();
    }
}
