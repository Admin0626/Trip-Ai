package com.trip.module.interaction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trip.common.exception.BizException;
import com.trip.common.result.PageResult;
import com.trip.common.result.ResultCode;
import com.trip.module.destination.entity.Destination;
import com.trip.module.destination.mapper.DestinationMapper;
import com.trip.module.interaction.dto.BookingCreateDTO;
import com.trip.module.interaction.dto.CommentCreateDTO;
import com.trip.module.interaction.entity.RouteBooking;
import com.trip.module.interaction.entity.RouteComment;
import com.trip.module.interaction.entity.RouteCommentLike;
import com.trip.module.interaction.entity.RouteFavorite;
import com.trip.module.interaction.entity.RouteLike;
import com.trip.module.interaction.mapper.RouteBookingMapper;
import com.trip.module.interaction.mapper.RouteCommentLikeMapper;
import com.trip.module.interaction.mapper.RouteCommentMapper;
import com.trip.module.interaction.mapper.RouteFavoriteMapper;
import com.trip.module.interaction.mapper.RouteLikeMapper;
import com.trip.module.interaction.service.InteractionService;
import com.trip.module.interaction.vo.BookingVO;
import com.trip.module.interaction.vo.CommentVO;
import com.trip.module.interaction.vo.FavoriteVO;
import com.trip.module.interaction.vo.LikeVO;
import com.trip.module.interaction.vo.RouteStatVO;
import com.trip.module.route.entity.Route;
import com.trip.module.route.mapper.RouteMapper;
import com.trip.module.route.vo.RoutePageVO;
import com.trip.module.user.entity.SysUser;
import com.trip.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 互动服务实现（INT-01~12）。
 * 核心原则（第 2 批清单 §2）：
 * - 点赞/收藏/评论点赞：先查后增删 + route 冗余列原子加减（like_count = like_count ± 1，禁止先读后算）
 * - 预约名额校验：SELECT ... FOR UPDATE 行锁防超卖（BR-INT-04）
 * - 所有 update/delete 先做归属校验，越权抛 403（答辩必问）
 */
@Service
@RequiredArgsConstructor
public class InteractionServiceImpl implements InteractionService {

    private final RouteLikeMapper routeLikeMapper;
    private final RouteFavoriteMapper routeFavoriteMapper;
    private final RouteBookingMapper routeBookingMapper;
    private final RouteCommentMapper routeCommentMapper;
    private final RouteCommentLikeMapper routeCommentLikeMapper;
    private final RouteMapper routeMapper;
    private final SysUserMapper sysUserMapper;
    private final DestinationMapper destinationMapper;

    // ============ 点赞 / 收藏（串行切换；重复请求会再次切换，并非严格幂等） ============

    @Override
    @Transactional
    public LikeVO toggleLike(Long userId, Long routeId) {
        Route route = requireRouteForUpdate(routeId);
        RouteLike exist = routeLikeMapper.selectOne(
                new LambdaQueryWrapper<RouteLike>()
                        .eq(RouteLike::getUserId, userId)
                        .eq(RouteLike::getRouteId, routeId));
            if (exist == null) {
                RouteLike like = new RouteLike();
                like.setUserId(userId);
                like.setRouteId(routeId);
                routeLikeMapper.insert(like);
                routeMapper.update(null, new LambdaUpdateWrapper<Route>()
                        .eq(Route::getId, routeId)
                        .setSql("like_count = like_count + 1"));
                return buildLikeVO(true, route.getLikeCount() + 1);
            }
            routeLikeMapper.deleteById(exist.getId());
            routeMapper.update(null, new LambdaUpdateWrapper<Route>()
                    .eq(Route::getId, routeId)
                    .setSql("like_count = like_count - 1"));
            return buildLikeVO(false, Math.max(0, route.getLikeCount() - 1));

    }

    @Override
    public LikeVO likeStatus(Long userId, Long routeId) {
        Route route = requireRoute(routeId);
        boolean liked = outLikeStatus(userId, routeId);
        LikeVO vo = new LikeVO();
        vo.setLiked(liked);
        vo.setLikeCount(route.getLikeCount());
        return vo;
    }

    /** 查询当前用户对某路线点赞状态（匿名返回 false） */
    private boolean outLikeStatus(Long userId, Long routeId) {
        if (userId == null) {
            return false;
        }
        Long cnt = routeLikeMapper.selectCount(new LambdaQueryWrapper<RouteLike>()
                .eq(RouteLike::getUserId, userId)
                .eq(RouteLike::getRouteId, routeId));
        return cnt != null && cnt > 0;
    }

    @Override
    @Transactional
    public FavoriteVO toggleFavorite(Long userId, Long routeId) {
        Route route = requireRouteForUpdate(routeId);
        RouteFavorite exist = routeFavoriteMapper.selectOne(
                new LambdaQueryWrapper<RouteFavorite>()
                        .eq(RouteFavorite::getUserId, userId)
                        .eq(RouteFavorite::getRouteId, routeId));
            if (exist == null) {
                RouteFavorite fav = new RouteFavorite();
                fav.setUserId(userId);
                fav.setRouteId(routeId);
                routeFavoriteMapper.insert(fav);
                routeMapper.update(null, new LambdaUpdateWrapper<Route>()
                        .eq(Route::getId, routeId)
                        .setSql("favorite_count = favorite_count + 1"));
                return buildFavoriteVO(true, route.getFavoriteCount() + 1);
            }
            routeFavoriteMapper.deleteById(exist.getId());
            routeMapper.update(null, new LambdaUpdateWrapper<Route>()
                    .eq(Route::getId, routeId)
                    .setSql("favorite_count = favorite_count - 1"));
            return buildFavoriteVO(false, Math.max(0, route.getFavoriteCount() - 1));

    }

    @Override
    public PageResult<RoutePageVO> myFavorites(Long userId, long current, long size) {
        Page<RouteFavorite> p = routeFavoriteMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<RouteFavorite>()
                        .eq(RouteFavorite::getUserId, userId)
                        .orderByDesc(RouteFavorite::getCreateTime));
        PageResult<RoutePageVO> pr = new PageResult<>();
        if (p.getRecords() == null || p.getRecords().isEmpty()) {
            pr.setRecords(Collections.emptyList());
            pr.setTotal(p.getTotal());
            pr.setCurrent(p.getCurrent());
            pr.setSize(p.getSize());
            pr.setPages(p.getPages());
            return pr;
        }
        Set<Long> routeIds = p.getRecords().stream()
                .map(RouteFavorite::getRouteId).collect(Collectors.toSet());
        List<Route> routes = routeMapper.selectBatchIds(routeIds);
        Map<Long, Route> routeMap = routes.stream()
                .collect(Collectors.toMap(Route::getId, Function.identity()));
        Set<Long> destIds = routes.stream().map(Route::getDestinationId).collect(Collectors.toSet());
        Map<Long, Destination> destMap = destinationMapper.selectBatchIds(destIds).stream()
                .collect(Collectors.toMap(Destination::getId, Function.identity()));
        // 我的收藏列表里：favorited 恒为 true，liked 单独查一次
        Set<Long> likedIds = routeLikeMapper.selectList(
                        new LambdaQueryWrapper<RouteLike>().eq(RouteLike::getUserId, userId))
                .stream().map(RouteLike::getRouteId).collect(Collectors.toSet());

        pr.setRecords(p.getRecords().stream()
                .filter(f -> routeMap.containsKey(f.getRouteId()))
                .map(f -> toRoutePageVO(routeMap.get(f.getRouteId()),
                        destMap.get(routeMap.get(f.getRouteId()).getDestinationId()), true, likedIds))
                .toList());
        pr.setTotal(p.getTotal());
        pr.setCurrent(p.getCurrent());
        pr.setSize(p.getSize());
        pr.setPages(p.getPages());
        return pr;
    }

    // ============ 预约（BR-INT-02/03/04） ============

    @Override
    @Transactional
    public BookingVO createBooking(Long userId, BookingCreateDTO dto) {
        SysUser bookingUser = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getId,userId).last("FOR UPDATE"));
        if(bookingUser==null || !Integer.valueOf(1).equals(bookingUser.getStatus()))
            throw new BizException(401,"账号已不可用，请重新登录");
        // ① BR-INT-02：出行日期 ≥ 明天
        if (dto.getTravelDate().isBefore(LocalDate.now().plusDays(1))) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "出行日期须为明天及以后");
        }
        // ② BR-INT-04 名额校验（★ 必须行锁，防并发超卖）：
        //    锁 route 行 → 查已确认人数 → 已确认 + 本次 ≤ quota_per_day
        Route route = requireRouteForUpdate(dto.getRouteId());
        // ③ BR-INT-03：同用户同路线同出行日期只能有 1 条未取消预约（status 0/1）
        Long dup = routeBookingMapper.selectCount(new LambdaQueryWrapper<RouteBooking>()
                .eq(RouteBooking::getUserId, userId)
                .eq(RouteBooking::getRouteId, dto.getRouteId())
                .eq(RouteBooking::getTravelDate, dto.getTravelDate())
                .in(RouteBooking::getStatus, 0, 1));
        if (dup != null && dup > 0) {
            throw new BizException(ResultCode.BOOKING_DUP);
        }
        checkBookingCapacity(route, dto.getTravelDate(), dto.getPeopleNum());
        // ④ 生成预约单
        RouteBooking booking = new RouteBooking();
        booking.setBookingNo(generateBookingNo());
        booking.setRouteId(dto.getRouteId());
        booking.setUserId(userId);
        booking.setTravelDate(dto.getTravelDate());
        booking.setPeopleNum(dto.getPeopleNum());
        booking.setContactName(dto.getContactName());
        booking.setContactPhone(dto.getContactPhone());
        booking.setRemark(dto.getRemark());
        booking.setStatus(0);
        // 显式赋值 createTime：MySQL DEFAULT CURRENT_TIMESTAMP 不会回填到实体，否则响应 createTime=null
        booking.setCreateTime(LocalDateTime.now());
        routeBookingMapper.insert(booking);

        // ⑤ 冗余计数 +1（booking_count 表示未取消的预约单数（0/1/3））
        routeMapper.update(null, new LambdaUpdateWrapper<Route>()
                .eq(Route::getId, dto.getRouteId())
                .setSql("booking_count = booking_count + 1"));

        BookingVO vo = toBookingVO(booking, route.getTitle());
        return vo;
    }

    @Override
    public PageResult<BookingVO> myBookings(Long userId, long current, long size) {
        Page<RouteBooking> p = routeBookingMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<RouteBooking>()
                        .eq(RouteBooking::getUserId, userId)
                        .orderByDesc(RouteBooking::getCreateTime));
        return bookingPageResult(p);
    }

    @Override
    @Transactional
    public void cancelBooking(Long userId, Long bookingId) {
        RouteBooking booking = routeBookingMapper.selectById(bookingId);
        if (booking == null || !booking.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        lockExistingRoute(booking.getRouteId());
        booking = routeBookingMapper.selectOne(new LambdaQueryWrapper<RouteBooking>()
                .eq(RouteBooking::getId, bookingId).last("FOR UPDATE"));
        // 08 §2.3 状态机：仅 0 待确认 / 1 已确认 可取消
        if (booking.getStatus() == 2) {
            throw new BizException(ResultCode.CONFLICT.getCode(), "该预约已取消");
        }
        if (booking.getStatus() == 3) {
            throw new BizException(ResultCode.CONFLICT.getCode(), "该预约已完成，不可取消");
        }
        // 取消后释放名额反馈到 booking_count
        routeBookingMapper.update(null, new LambdaUpdateWrapper<RouteBooking>()
                .eq(RouteBooking::getId, bookingId)
                .set(RouteBooking::getStatus, 2)
                .set(RouteBooking::getCancelTime, LocalDateTime.now()));
        routeMapper.update(null, new LambdaUpdateWrapper<Route>()
                .eq(Route::getId, booking.getRouteId())
                .setSql("booking_count = booking_count - 1"));
    }

    @Override
    public PageResult<BookingVO> adminBookingPage(long current, long size, Integer status, Long routeId) {
        LambdaQueryWrapper<RouteBooking> w = new LambdaQueryWrapper<>();
        if (status != null) {
            w.eq(RouteBooking::getStatus, status);
        }
        if (routeId != null) {
            w.eq(RouteBooking::getRouteId, routeId);
        }
        w.orderByDesc(RouteBooking::getCreateTime);
        return bookingPageResult(routeBookingMapper.selectPage(new Page<>(current, size), w));
    }

    @Override
    @Transactional
    public void adminUpdateBookingStatus(Long auditBy, Long bookingId, Integer status) {
        RouteBooking booking = routeBookingMapper.selectById(bookingId);
        if (booking == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        Route lockedRoute = lockExistingRoute(booking.getRouteId());
        booking = routeBookingMapper.selectOne(new LambdaQueryWrapper<RouteBooking>()
                .eq(RouteBooking::getId, bookingId).last("FOR UPDATE"));
        // 08 §2.3 状态机：0 待确认 → 1 已确认 / 2 已取消；1 → 3 已完成；已取消不可再变更
        int current = booking.getStatus();
        boolean legal = switch (status) {
            case 1 -> current == 0;
            case 2 -> current == 0;
            case 3 -> current == 1;
            default -> false;
        };
        if (!legal) {
            throw new BizException(ResultCode.CONFLICT.getCode(),
                    "非法状态流转：当前状态不可变更为目标状态");
        }
        if (status == 1) {
            checkBookingCapacity(lockedRoute,
                    booking.getTravelDate(), booking.getPeopleNum());
        }
        if (status == 2) {
            routeMapper.update(null, new LambdaUpdateWrapper<Route>()
                    .eq(Route::getId, booking.getRouteId()).setSql("booking_count = booking_count - 1"));
        }
        routeBookingMapper.update(null, new LambdaUpdateWrapper<RouteBooking>()
                .eq(RouteBooking::getId, bookingId)
                .set(RouteBooking::getStatus, status)
                .set(status == 2, RouteBooking::getCancelTime, LocalDateTime.now())
                .set(RouteBooking::getAuditBy, auditBy)
                .set(RouteBooking::getAuditTime, LocalDateTime.now()));
    }

    // ============ 评论（BR-INT-05/06/07/08） ============

    @Override
    @Transactional
    public CommentVO createComment(Long userId, CommentCreateDTO dto) {
        requireRouteForUpdate(dto.getRouteId());
        // BR-INT-05：每人每路线每天最多 3 条
        Long daily = routeCommentMapper.selectCount(new LambdaQueryWrapper<RouteComment>()
                .eq(RouteComment::getUserId, userId)
                .eq(RouteComment::getRouteId, dto.getRouteId())
                .ge(RouteComment::getCreateTime, LocalDate.now().atStartOfDay()));
        if (daily != null && daily >= 3) {
            throw new BizException(ResultCode.COMMENT_TOO_MANY);
        }
        // BR-INT-07：回复层级最多 2 层，parentId 须为存在的顶级评论
        Long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        if (parentId > 0) {
            RouteComment parent = routeCommentMapper.selectById(parentId);
            if (parent == null || !parent.getRouteId().equals(dto.getRouteId())
                    || parent.getStatus() != 1 || parent.getParentId() != null && parent.getParentId() > 0) {
                throw new BizException(ResultCode.BAD_REQUEST.getCode(), "仅支持对顶级评论进行回复");
            }
        }
        RouteComment comment = new RouteComment();
        comment.setRouteId(dto.getRouteId());
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setScore(dto.getScore());
        comment.setContent(dto.getContent());
        comment.setImages(dto.getImages() == null ? null : String.join(",", dto.getImages()));
        comment.setLikeCount(0);
        comment.setSentiment("unknown");
        comment.setStatus(1);
        routeCommentMapper.insert(comment);

        // 冗余计数 + 平均分重算（评分只统计显示中的评论）
        routeMapper.update(null, new LambdaUpdateWrapper<Route>()
                .eq(Route::getId, dto.getRouteId())
                .setSql("comment_count = comment_count + 1"));
        recalcAvgScore(dto.getRouteId());

        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setSentiment("unknown");
        return vo;
    }

    @Override
    public PageResult<CommentVO> commentPage(Long routeId, long current, long size,
                                             String sortBy, String sentiment, Long currentUserId) {
        requireRoute(routeId);
        LambdaQueryWrapper<RouteComment> w = new LambdaQueryWrapper<RouteComment>()
                .eq(RouteComment::getRouteId, routeId)
                .eq(RouteComment::getStatus, 1);
        if (sentiment != null && !sentiment.isBlank() && !"all".equals(sentiment)) {
            w.eq(RouteComment::getSentiment, sentiment);
        }
        if ("score".equals(sortBy)) {
            w.orderByDesc(RouteComment::getScore).orderByDesc(RouteComment::getCreateTime);
        } else {
            w.orderByDesc(RouteComment::getCreateTime);
        }
        Page<RouteComment> p = routeCommentMapper.selectPage(new Page<>(current, size), w);
        return commentPageResult(p, currentUserId);
    }

    @Override
    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        RouteComment comment = routeCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }

        lockExistingRoute(comment.getRouteId());
        comment = routeCommentMapper.selectOne(new LambdaQueryWrapper<RouteComment>()
                .eq(RouteComment::getId, commentId).last("FOR UPDATE"));
        if (comment == null) throw new BizException(ResultCode.NOT_FOUND);
        // BR-INT-08：仅作者本人可删（管理员走 adminHideComment 隐藏）
        if (!comment.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        routeCommentMapper.deleteById(commentId);
        routeMapper.update(null, new LambdaUpdateWrapper<Route>()
                .eq(Route::getId, comment.getRouteId())
                .setSql("comment_count = comment_count - 1"));
        recalcAvgScore(comment.getRouteId());
    }

    @Override
    @Transactional
    public LikeVO toggleCommentLike(Long userId, Long commentId) {
        RouteComment comment = routeCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }

        requireRouteForUpdate(comment.getRouteId());
        comment = routeCommentMapper.selectOne(new LambdaQueryWrapper<RouteComment>()
                .eq(RouteComment::getId, commentId).last("FOR UPDATE"));
        if (comment == null) throw new BizException(ResultCode.NOT_FOUND);
        RouteCommentLike exist = routeCommentLikeMapper.selectOne(
                new LambdaQueryWrapper<RouteCommentLike>()
                        .eq(RouteCommentLike::getUserId, userId)
                        .eq(RouteCommentLike::getCommentId, commentId).last("FOR UPDATE"));
            if (exist == null) {
                RouteCommentLike like = new RouteCommentLike();
                like.setUserId(userId);
                like.setCommentId(commentId);
                routeCommentLikeMapper.insert(like);
                routeCommentMapper.update(null, new LambdaUpdateWrapper<RouteComment>()
                        .eq(RouteComment::getId, commentId)
                        .setSql("like_count = like_count + 1"));
                return buildLikeVO(true, comment.getLikeCount() + 1);
            }
            routeCommentLikeMapper.deleteById(exist.getId());
            routeCommentMapper.update(null, new LambdaUpdateWrapper<RouteComment>()
                    .eq(RouteComment::getId, commentId)
                    .setSql("like_count = like_count - 1"));
            return buildLikeVO(false, Math.max(0, comment.getLikeCount() - 1));

    }

    @Override
    public PageResult<CommentVO> adminCommentPage(long current, long size, Integer status, Long routeId) {
        LambdaQueryWrapper<RouteComment> w = new LambdaQueryWrapper<>();
        if (status != null) {
            w.eq(RouteComment::getStatus, status);
        }
        if (routeId != null) {
            w.eq(RouteComment::getRouteId, routeId);
        }
        w.orderByDesc(RouteComment::getCreateTime);
        return commentPageResult(routeCommentMapper.selectPage(new Page<>(current, size), w), null);
    }

    @Override
    @Transactional
    public void adminHideComment(Long auditBy, Long commentId, Integer status) {
        RouteComment comment = routeCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }

        lockExistingRoute(comment.getRouteId());
        comment = routeCommentMapper.selectOne(new LambdaQueryWrapper<RouteComment>()
                .eq(RouteComment::getId, commentId).last("FOR UPDATE"));
        if (comment == null) throw new BizException(ResultCode.NOT_FOUND);
        if (status == null || status != 0 && status != 1) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "评论状态仅支持 0 隐藏 / 1 显示");
        }
        routeCommentMapper.update(null, new LambdaUpdateWrapper<RouteComment>()
                .eq(RouteComment::getId, commentId)
                .set(RouteComment::getStatus, status));
        recalcAvgScore(comment.getRouteId());
    }

    @Override
    public RouteStatVO adminStat(Long routeId) {
        Route route = routeMapper.selectById(routeId);
        if (route == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        RouteStatVO vo = new RouteStatVO();
        vo.setRouteId(route.getId());
        vo.setLikeCount(route.getLikeCount());
        vo.setFavoriteCount(route.getFavoriteCount());
        vo.setBookingCount(route.getBookingCount());
        vo.setCommentCount(route.getCommentCount());
        vo.setViewCount(route.getViewCount());
        vo.setAvgScore(route.getAvgScore());
        return vo;
    }

    // ============ 私有组装 ============

    private Route requireRoute(Long routeId) {
        Route route = routeMapper.selectById(routeId);
        Destination parent = route == null ? null : destinationMapper.selectById(route.getDestinationId());
        if (route == null || !Integer.valueOf(1).equals(route.getStatus()) || parent == null || !Integer.valueOf(1).equals(parent.getStatus())) {
            throw new BizException(ResultCode.ROUTE_NOT_FOUND);
        }
        return route;
    }

    /** 行锁读路线（BR-INT-04 防超卖）：本事务内该行被锁，其他并发预约等待 */
    private Route requireRouteForUpdate(Long routeId) {
        Route route = lockExistingRoute(routeId);
        Destination parent = destinationMapper.selectOne(new LambdaQueryWrapper<Destination>()
                .eq(Destination::getId, route.getDestinationId()).last("FOR UPDATE"));
        if (!Integer.valueOf(1).equals(route.getStatus()) || parent == null || !Integer.valueOf(1).equals(parent.getStatus()))
            throw new BizException(ResultCode.ROUTE_NOT_FOUND);
        return route;
    }

    /** Historical records remain manageable when the route or its destination is offline. */
    private Route lockExistingRoute(Long routeId) {
        List<Route> list = routeMapper.selectList(
                new LambdaQueryWrapper<Route>()
                        .eq(Route::getId, routeId)
                        .last("FOR UPDATE"));
        if (list == null || list.isEmpty()) {
            throw new BizException(ResultCode.ROUTE_NOT_FOUND);
        }
        return list.get(0);
    }

    private LikeVO buildLikeVO(boolean liked, int likeCount) {
        LikeVO vo = new LikeVO();
        vo.setLiked(liked);
        vo.setLikeCount(likeCount);
        return vo;
    }

    private FavoriteVO buildFavoriteVO(boolean favorited, int favoriteCount) {
        FavoriteVO vo = new FavoriteVO();
        vo.setFavorited(favorited);
        vo.setFavoriteCount(favoriteCount);
        return vo;
    }

    /** 跨路线并发也唯一，不依赖查询最大流水。32位，适配 booking_no varchar(32)。 */
    private String generateBookingNo() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /** 待审核不占名额；提交与确认均检查，确认操作共享路线行锁。 */
    private void checkBookingCapacity(Route route, LocalDate date, int people) {
        int confirmed = routeBookingMapper.selectList(new LambdaQueryWrapper<RouteBooking>()
                .eq(RouteBooking::getRouteId, route.getId())
                .eq(RouteBooking::getTravelDate, date)
                .eq(RouteBooking::getStatus, 1).last("FOR UPDATE"))
                .stream().mapToInt(RouteBooking::getPeopleNum).sum();
        if (confirmed + people > route.getQuotaPerDay()) throw new BizException(ResultCode.BOOKING_FULL);
    }

    private void recalcAvgScore(Long routeId) {
        List<RouteComment> shown = routeCommentMapper.selectList(
                new LambdaQueryWrapper<RouteComment>()
                        .eq(RouteComment::getRouteId, routeId)
                        .eq(RouteComment::getStatus, 1).last("FOR UPDATE"));
        BigDecimal avg = BigDecimal.ZERO;
        if (shown != null && !shown.isEmpty()) {
            double sum = shown.stream().mapToInt(RouteComment::getScore).sum();
            avg = BigDecimal.valueOf(sum / shown.size()).setScale(2, RoundingMode.HALF_UP);
        }
        routeMapper.update(null, new LambdaUpdateWrapper<Route>()
                .eq(Route::getId, routeId)
                .set(Route::getAvgScore, avg));
    }

    // ---------- 分页组装 ----------

    private PageResult<BookingVO> bookingPageResult(Page<RouteBooking> p) {
        PageResult<BookingVO> pr = new PageResult<>();
        if (p.getRecords() == null || p.getRecords().isEmpty()) {
            pr.setRecords(Collections.emptyList());
            pr.setTotal(p.getTotal());
            pr.setCurrent(p.getCurrent());
            pr.setSize(p.getSize());
            pr.setPages(p.getPages());
            return pr;
        }
        Set<Long> routeIds = p.getRecords().stream()
                .map(RouteBooking::getRouteId).collect(Collectors.toSet());
        Map<Long, String> titleMap = routeMapper.selectBatchIds(routeIds).stream()
                .collect(Collectors.toMap(Route::getId, Route::getTitle));
        pr.setRecords(p.getRecords().stream()
                .map(b -> toBookingVO(b, titleMap.get(b.getRouteId())))
                .toList());
        pr.setTotal(p.getTotal());
        pr.setCurrent(p.getCurrent());
        pr.setSize(p.getSize());
        pr.setPages(p.getPages());
        return pr;
    }

    private BookingVO toBookingVO(RouteBooking b, String routeTitle) {
        BookingVO vo = new BookingVO();
        vo.setBookingId(b.getId());
        vo.setBookingNo(b.getBookingNo());
        vo.setRouteId(b.getRouteId());
        vo.setRouteTitle(routeTitle);
        vo.setTravelDate(b.getTravelDate());
        vo.setPeopleNum(b.getPeopleNum());
        vo.setContactName(b.getContactName());
        vo.setContactPhone(b.getContactPhone());
        vo.setRemark(b.getRemark());
        vo.setStatus(b.getStatus());
        vo.setCreateTime(b.getCreateTime());
        return vo;
    }

    private PageResult<CommentVO> commentPageResult(Page<RouteComment> p, Long currentUserId) {
        PageResult<CommentVO> pr = new PageResult<>();
        if (p.getRecords() == null || p.getRecords().isEmpty()) {
            pr.setRecords(Collections.emptyList());
            pr.setTotal(p.getTotal());
            pr.setCurrent(p.getCurrent());
            pr.setSize(p.getSize());
            pr.setPages(p.getPages());
            return pr;
        }
        Set<Long> userIds = p.getRecords().stream()
                .map(RouteComment::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> userMap = sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Set<Long> likedCommentIds = Collections.emptySet();
        if (currentUserId != null) {
            likedCommentIds = routeCommentLikeMapper.selectList(
                            new LambdaQueryWrapper<RouteCommentLike>()
                                    .eq(RouteCommentLike::getUserId, currentUserId)
                                    .in(RouteCommentLike::getCommentId,
                                            p.getRecords().stream().map(RouteComment::getId).toList()))
                    .stream().map(RouteCommentLike::getCommentId).collect(Collectors.toSet());
        }
        Set<Long> finalLiked = likedCommentIds;
        pr.setRecords(p.getRecords().stream()
                .map(c -> toCommentVO(c, userMap.get(c.getUserId()), finalLiked))
                .toList());
        pr.setTotal(p.getTotal());
        pr.setCurrent(p.getCurrent());
        pr.setSize(p.getSize());
        pr.setPages(p.getPages());
        return pr;
    }

    private CommentVO toCommentVO(RouteComment c, SysUser user, Set<Long> likedCommentIds) {
        CommentVO vo = new CommentVO();
        vo.setId(c.getId());
        vo.setRouteId(c.getRouteId());
        vo.setUserId(c.getUserId());
        vo.setUserNickname(user != null && user.getNickname() != null ? user.getNickname() : "游客");
        vo.setUserAvatar(user != null ? user.getAvatar() : null);
        vo.setParentId(c.getParentId());
        vo.setScore(c.getScore());
        vo.setStatus(c.getStatus());
        vo.setContent(c.getContent());
        vo.setImages(c.getImages() == null || c.getImages().isBlank()
                ? Collections.emptyList()
                : List.of(c.getImages().split(",")));
        vo.setLikeCount(c.getLikeCount());
        vo.setLiked(likedCommentIds.contains(c.getId()));
        vo.setSentiment(c.getSentiment());
        vo.setSentimentScore(c.getSentimentScore());
        vo.setCreateTime(c.getCreateTime());
        return vo;
    }

    private RoutePageVO toRoutePageVO(Route r, Destination dest, boolean favorited, Set<Long> likedIds) {
        RoutePageVO vo = new RoutePageVO();
        vo.setId(r.getId());
        vo.setTitle(r.getTitle());
        vo.setSubtitle(r.getSubtitle());
        vo.setCoverImg(r.getCoverImg());
        vo.setDestinationName(dest != null ? dest.getName() : "");
        vo.setDays(r.getDays());
        vo.setPrice(r.getPrice());
        vo.setDifficulty(r.getDifficulty());
        vo.setTags(RoutePageVO.splitTags(r.getTags()));
        vo.setLikeCount(r.getLikeCount());
        vo.setFavoriteCount(r.getFavoriteCount());
        vo.setBookingCount(r.getBookingCount());
        vo.setCommentCount(r.getCommentCount());
        vo.setViewCount(r.getViewCount());
        vo.setAvgScore(r.getAvgScore());
        vo.setIsTop(r.getIsTop());
        vo.setLiked(likedIds.contains(r.getId()));
        vo.setFavorited(favorited);
        return vo;
    }
}
