package com.trip.module.interaction.controller;

import com.trip.common.result.PageResult;
import com.trip.common.result.R;
import com.trip.common.result.ResultCode;
import com.trip.module.interaction.dto.BookingCreateDTO;
import com.trip.module.interaction.dto.CommentCreateDTO;
import com.trip.module.interaction.dto.FavoriteDTO;
import com.trip.module.interaction.dto.LikeDTO;
import com.trip.module.interaction.service.InteractionService;
import com.trip.module.interaction.vo.BookingVO;
import com.trip.module.interaction.vo.CommentVO;
import com.trip.module.interaction.vo.FavoriteVO;
import com.trip.module.interaction.vo.LikeVO;
import com.trip.module.route.vo.RoutePageVO;
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
 * 互动接口（api/03 · /interaction/**，除评论分页外均需登录 USER）
 */
@RestController
@RequestMapping("/interaction")
@RequiredArgsConstructor
public class InteractionController {

    private final InteractionService interactionService;

    /** POST /interaction/like 点赞 / 取消点赞 */
    @PostMapping("/like")
    public R<LikeVO> like(@Valid @RequestBody LikeDTO dto) {
        return R.ok(interactionService.toggleLike(currentUserId(), dto.getRouteId()));
    }

    /** GET /interaction/like/{routeId}/status 查询点赞状态 */
    @GetMapping("/like/{routeId}/status")
    public R<LikeVO> likeStatus(@PathVariable Long routeId) {
        return R.ok(interactionService.likeStatus(currentUserId(), routeId));
    }

    /** POST /interaction/favorite 收藏 / 取消收藏 */
    @PostMapping("/favorite")
    public R<FavoriteVO> favorite(@Valid @RequestBody FavoriteDTO dto) {
        return R.ok(interactionService.toggleFavorite(currentUserId(), dto.getRouteId()));
    }

    /** GET /interaction/favorite/page 我的收藏（分页） */
    @GetMapping("/favorite/page")
    public R<PageResult<RoutePageVO>> favoritePage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        if (current < 1 || size < 1) throw new com.trip.common.exception.BizException(400, "分页参数须大于0");
        size = Math.min(size, 100);
        return R.ok(interactionService.myFavorites(currentUserId(), current, size));
    }

    /** POST /interaction/booking 提交预约 */
    @PostMapping("/booking")
    public R<BookingVO> createBooking(@Valid @RequestBody BookingCreateDTO dto) {
        return R.ok(interactionService.createBooking(currentUserId(), dto));
    }

    /** GET /interaction/booking/page 我的预约列表 */
    @GetMapping("/booking/page")
    public R<PageResult<BookingVO>> bookingPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        if (current < 1 || size < 1) throw new com.trip.common.exception.BizException(400, "分页参数须大于0");
        size = Math.min(size, 100);
        return R.ok(interactionService.myBookings(currentUserId(), current, size));
    }

    /** PUT /interaction/booking/{id}/cancel 取消预约 */
    @PutMapping("/booking/{id}/cancel")
    public R<Void> cancelBooking(@PathVariable Long id) {
        interactionService.cancelBooking(currentUserId(), id);
        return R.ok();
    }

    /** POST /interaction/comment 发表评论 */
    @PostMapping("/comment")
    public R<CommentVO> createComment(@Valid @RequestBody CommentCreateDTO dto) {
        return R.ok(interactionService.createComment(currentUserId(), dto));
    }

    /** GET /interaction/comment/page 路线评论分页（公开） */
    @GetMapping("/comment/page")
    public R<PageResult<CommentVO>> commentPage(
            @RequestParam Long routeId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sentiment) {
        if (current < 1 || size < 1) throw new com.trip.common.exception.BizException(400, "分页参数须大于0");
        size = Math.min(size, 100);
        return R.ok(interactionService.commentPage(routeId, current, size, sortBy, sentiment,
                currentUserIdOrNull()));
    }

    /** DELETE /interaction/comment/{id} 删除自己的评论 */
    @DeleteMapping("/comment/{id}")
    public R<Void> deleteComment(@PathVariable Long id) {
        interactionService.deleteComment(currentUserId(), id);
        return R.ok();
    }

    /** POST /interaction/comment/{id}/like 评论点赞 */
    @PostMapping("/comment/{id}/like")
    public R<LikeVO> commentLike(@PathVariable Long id) {
        return R.ok(interactionService.toggleCommentLike(currentUserId(), id));
    }

    // ---------------- 当前用户 ----------------

    /** 未登录返回 401（USER 接口全部要求登录） */
    private Long currentUserId() {
        Long userId = currentUserIdOrNull();
        if (userId == null) {
            throw new com.trip.common.exception.BizException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    private Long currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long)) {
            return null;
        }
        return (Long) auth.getPrincipal();
    }
}