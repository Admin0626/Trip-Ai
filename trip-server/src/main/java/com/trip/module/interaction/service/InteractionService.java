package com.trip.module.interaction.service;

import com.trip.common.result.PageResult;
import com.trip.module.interaction.dto.BookingCreateDTO;
import com.trip.module.interaction.dto.CommentCreateDTO;
import com.trip.module.interaction.vo.BookingVO;
import com.trip.module.interaction.vo.CommentVO;
import com.trip.module.interaction.vo.FavoriteVO;
import com.trip.module.interaction.vo.LikeVO;
import com.trip.module.interaction.vo.RouteStatVO;
import com.trip.module.route.vo.RoutePageVO;

/**
 * 互动服务（INT-01~12）。
 */
public interface InteractionService {

    // ---------- 点赞 / 收藏（INT-01/02/03，BR-INT-01 幂等开关） ----------

    LikeVO toggleLike(Long userId, Long routeId);

    /** 查询点赞状态（api/03 · GET /interaction/like/{routeId}/status，INT-01 回显） */
    LikeVO likeStatus(Long userId, Long routeId);

    FavoriteVO toggleFavorite(Long userId, Long routeId);

    /** 我的收藏分页（INT-03），返回路线列表（RouteCard 复用） */
    PageResult<RoutePageVO> myFavorites(Long userId, long current, long size);

    // ---------- 预约（INT-04/05/06，BR-INT-02/03/04） ----------

    BookingVO createBooking(Long userId, BookingCreateDTO dto);

    PageResult<BookingVO> myBookings(Long userId, long current, long size);

    void cancelBooking(Long userId, Long bookingId);

    /** 管理端：全部预约分页（INT-06） */
    PageResult<BookingVO> adminBookingPage(long current, long size, Integer status, Long routeId);

    /** 管理端：审核预约状态（INT-06，状态机见 08 §2.3） */
    void adminUpdateBookingStatus(Long auditBy, Long bookingId, Integer status);

    // ---------- 评论（INT-07/08/09/10/13，BR-INT-05/06/07/08） ----------

    CommentVO createComment(Long userId, CommentCreateDTO dto);

    PageResult<CommentVO> commentPage(Long routeId, long current, long size, String sortBy,
                                      String sentiment, Long currentUserId);

    void deleteComment(Long userId, Long commentId);

    LikeVO toggleCommentLike(Long userId, Long commentId);

    /** 管理端：评论管理分页 */
    PageResult<CommentVO> adminCommentPage(long current, long size, Integer status, Long routeId);

    /** 管理端：显示 / 隐藏评论（BR-INT-08） */
    void adminHideComment(Long auditBy, Long commentId, Integer status);

    /** 管理端：单条路线互动统计（INT-12） */
    RouteStatVO adminStat(Long routeId);
}