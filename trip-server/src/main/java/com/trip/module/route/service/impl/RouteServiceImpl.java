package com.trip.module.route.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trip.common.exception.BizException;
import com.trip.common.result.PageResult;
import com.trip.common.result.ResultCode;
import com.trip.module.destination.entity.Destination;
import com.trip.module.destination.mapper.DestinationMapper;
import com.trip.module.route.entity.Route;
import com.trip.module.route.entity.RouteDay;
import com.trip.module.route.entity.RouteItem;
import com.trip.module.route.mapper.RouteDayMapper;
import com.trip.module.route.mapper.RouteItemMapper;
import com.trip.module.route.mapper.RouteMapper;
import com.trip.module.route.service.RouteService;
import com.trip.module.route.vo.RouteDayVO;
import com.trip.module.route.vo.RouteDetailVO;
import com.trip.module.route.vo.RouteItemVO;
import com.trip.module.route.vo.RoutePageVO;
import com.trip.module.interaction.entity.RouteFavorite;
import com.trip.module.interaction.entity.RouteLike;
import com.trip.module.interaction.mapper.RouteFavoriteMapper;
import com.trip.module.interaction.mapper.RouteLikeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RouteServiceImpl implements RouteService {

    private final RouteMapper routeMapper;
    private final RouteDayMapper routeDayMapper;
    private final RouteItemMapper routeItemMapper;
    private final DestinationMapper destinationMapper;
    private final RouteLikeMapper routeLikeMapper;
    private final RouteFavoriteMapper routeFavoriteMapper;

    @Override
    public PageResult<RoutePageVO> page(long current, long size, String keyword, Long destinationId,
                                        Integer days, BigDecimal priceMin, BigDecimal priceMax,
                                        Integer difficulty, String tag, String sortBy, Integer status) {
        LambdaQueryWrapper<Route> w = new LambdaQueryWrapper<>();
        w.eq(Route::getStatus, status != null ? status : 1);
        List<Long> activeDestinationIds = destinationMapper.selectList(new LambdaQueryWrapper<Destination>().eq(Destination::getStatus, 1))
                .stream().map(Destination::getId).toList();
        if (activeDestinationIds.isEmpty()) return new PageResult<>();
        w.in(Route::getDestinationId, activeDestinationIds);
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(Route::getTitle, keyword).or().like(Route::getSubtitle, keyword));
        }
        if (destinationId != null) {
            w.eq(Route::getDestinationId, destinationId);
        }
        if (days != null) {
            w.eq(Route::getDays, days);
        }
        if (priceMin != null) {
            w.ge(Route::getPrice, priceMin);
        }
        if (priceMax != null) {
            w.le(Route::getPrice, priceMax);
        }
        if (difficulty != null) {
            w.eq(Route::getDifficulty, difficulty);
        }
        if (StringUtils.hasText(tag)) {
            w.like(Route::getTags, tag);
        }
        applySort(w, sortBy);

        Page<Route> p = routeMapper.selectPage(new Page<>(current, size), w);
        PageResult<RoutePageVO> pr = new PageResult<>();
        pr.setRecords(toPageVOList(p.getRecords()));
        pr.setTotal(p.getTotal());
        pr.setCurrent(p.getCurrent());
        pr.setSize(p.getSize());
        pr.setPages(p.getPages());
        return pr;
    }

    @Override
    public RouteDetailVO detail(Long id) {
        Route route = loadOnShelfRoute(id);

        Destination dest = destinationMapper.selectById(route.getDestinationId());
        List<RouteDay> days = routeDayMapper.selectList(
                new LambdaQueryWrapper<RouteDay>()
                        .eq(RouteDay::getRouteId, id)
                        .orderByAsc(RouteDay::getDayIndex));

        RouteDetailVO vo = new RouteDetailVO();
        vo.setId(route.getId());
        vo.setTitle(route.getTitle());
        vo.setSubtitle(route.getSubtitle());
        vo.setCoverImg(route.getCoverImg());
        vo.setDestination(RouteDetailVO.DestinationRef.from(dest));
        vo.setDays(route.getDays());
        vo.setPrice(route.getPrice());
        vo.setDifficulty(route.getDifficulty());
        vo.setTags(RoutePageVO.splitTags(route.getTags()));
        vo.setHighlights(route.getHighlights());
        vo.setNotice(route.getNotice());
        vo.setLikeCount(route.getLikeCount());
        vo.setFavoriteCount(route.getFavoriteCount());
        vo.setBookingCount(route.getBookingCount());
        vo.setCommentCount(route.getCommentCount());
        vo.setViewCount(route.getViewCount());
        vo.setAvgScore(route.getAvgScore());
        Long userId = currentUserId();
        Long likedCnt = userId == null ? 0L : routeLikeMapper.selectCount(
                new LambdaQueryWrapper<RouteLike>()
                        .eq(RouteLike::getUserId, userId)
                        .eq(RouteLike::getRouteId, id));
        Long favCnt = userId == null ? 0L : routeFavoriteMapper.selectCount(
                new LambdaQueryWrapper<RouteFavorite>()
                        .eq(RouteFavorite::getUserId, userId)
                        .eq(RouteFavorite::getRouteId, id));
        vo.setLiked(likedCnt != null && likedCnt > 0);
        vo.setFavorited(favCnt != null && favCnt > 0);
        vo.setDayList(buildDayVOs(days));
        return vo;
    }

    @Override
    public List<RouteDayVO> days(Long id) {
        loadOnShelfRoute(id);
        List<RouteDay> days = routeDayMapper.selectList(
                new LambdaQueryWrapper<RouteDay>()
                        .eq(RouteDay::getRouteId, id)
                        .orderByAsc(RouteDay::getDayIndex));
        return buildDayVOs(days);
    }

    @Override
    public List<RoutePageVO> hot(int limit) {
        Set<Long> active = activeDestinationIds();
        if (active.isEmpty()) return Collections.emptyList();
        List<Route> list = routeMapper.selectList(
                new LambdaQueryWrapper<Route>()
                        .eq(Route::getStatus, 1)
                        .in(!active.isEmpty(), Route::getDestinationId, active)
                        .orderByDesc(Route::getViewCount)
                        .last("LIMIT " + Math.max(1, limit)));
        return toPageVOList(list);
    }

    @Override
    public List<RoutePageVO> recommendHome() {
        Set<Long> active = activeDestinationIds();
        if (active.isEmpty()) return Collections.emptyList();
        List<Route> list = routeMapper.selectList(
                new LambdaQueryWrapper<Route>()
                        .eq(Route::getStatus, 1)
                        .in(!active.isEmpty(), Route::getDestinationId, active)
                        .orderByDesc(Route::getIsTop)
                        .orderByDesc(Route::getRecommendWeight)
                        .orderByDesc(Route::getViewCount)
                        .last("LIMIT 6"));
        return toPageVOList(list);
    }

    @Override
    public List<String> searchSuggest(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        Set<Long> active = activeDestinationIds();
        if (active.isEmpty()) return Collections.emptyList();
        List<Route> list = routeMapper.selectList(
                new LambdaQueryWrapper<Route>()
                        .eq(Route::getStatus, 1)
                        .in(Route::getDestinationId, active)
                        .and(q -> q.like(Route::getTitle, keyword).or().like(Route::getSubtitle, keyword))
                        .orderByDesc(Route::getViewCount)
                        .last("LIMIT 10"));
        return list.stream().map(Route::getTitle).distinct().toList();
    }

    // ---------------- 私有组装 ----------------

    private Route loadOnShelfRoute(Long id) {
        Route route = routeMapper.selectById(id);
        Destination parent = route == null ? null : destinationMapper.selectById(route.getDestinationId());
        if (route == null || !Integer.valueOf(1).equals(route.getStatus()) || parent == null || !Integer.valueOf(1).equals(parent.getStatus())) {
            throw new BizException(ResultCode.ROUTE_NOT_FOUND);
        }
        return route;
    }

    private Set<Long> activeDestinationIds() {
        return destinationMapper.selectList(new LambdaQueryWrapper<Destination>().eq(Destination::getStatus, 1))
                .stream().map(Destination::getId).collect(Collectors.toSet());
    }

    private void applySort(LambdaQueryWrapper<Route> w, String sortBy) {
        switch (sortBy == null ? "" : sortBy) {
            case "hot" -> w.orderByDesc(Route::getViewCount);
            case "new" -> w.orderByDesc(Route::getCreateTime);
            case "priceAsc" -> w.orderByAsc(Route::getPrice);
            case "priceDesc" -> w.orderByDesc(Route::getPrice);
            case "favorite" -> w.orderByDesc(Route::getFavoriteCount);
            default -> w.orderByDesc(Route::getIsTop).orderByDesc(Route::getViewCount);
        }
    }

    /** 一次查完目的地再组装（避免循环单查）；列表页 liked/favorited 也按当前用户批量查 */
    private List<RoutePageVO> toPageVOList(List<Route> routes) {
        if (routes == null || routes.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> destIds = routes.stream().map(Route::getDestinationId).collect(Collectors.toSet());
        Map<Long, Destination> destMap = destinationMapper.selectBatchIds(destIds).stream()
                .collect(Collectors.toMap(Destination::getId, Function.identity()));
        Long userId = currentUserId();
        Set<Long> likedIds = likedRouteIds(userId, routes);
        Set<Long> favoritedIds = favoritedRouteIds(userId, routes);
        return routes.stream()
                .map(r -> toPageVO(r, destMap.get(r.getDestinationId()),
                        likedIds.contains(r.getId()), favoritedIds.contains(r.getId())))
                .toList();
    }

    /** 当前登录用户（匿名返回 null，JWT principal 存的是 userId） */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long)) {
            return null;
        }
        return (Long) auth.getPrincipal();
    }

    private Set<Long> likedRouteIds(Long userId, List<Route> routes) {
        if (userId == null) {
            return Collections.emptySet();
        }
        List<Long> ids = routes.stream().map(Route::getId).toList();
        return routeLikeMapper.selectList(new LambdaQueryWrapper<RouteLike>()
                        .eq(RouteLike::getUserId, userId)
                        .in(RouteLike::getRouteId, ids))
                .stream().map(RouteLike::getRouteId).collect(Collectors.toSet());
    }

    private Set<Long> favoritedRouteIds(Long userId, List<Route> routes) {
        if (userId == null) {
            return Collections.emptySet();
        }
        List<Long> ids = routes.stream().map(Route::getId).toList();
        return routeFavoriteMapper.selectList(new LambdaQueryWrapper<RouteFavorite>()
                        .eq(RouteFavorite::getUserId, userId)
                        .in(RouteFavorite::getRouteId, ids))
                .stream().map(RouteFavorite::getRouteId).collect(Collectors.toSet());
    }

    private RoutePageVO toPageVO(Route r, Destination dest,
                                 boolean liked, boolean favorited) {
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
        vo.setLiked(liked);
        vo.setFavorited(favorited);
        return vo;
    }

    private List<RouteDayVO> buildDayVOs(List<RouteDay> days) {
        if (days == null || days.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> dayIds = days.stream().map(RouteDay::getId).toList();
        List<RouteItem> items = routeItemMapper.selectList(
                new LambdaQueryWrapper<RouteItem>()
                        .in(RouteItem::getRouteDayId, dayIds)
                        .orderByAsc(RouteItem::getSortNo));
        Map<Long, List<RouteItemVO>> itemMap = items.stream()
                .collect(Collectors.groupingBy(RouteItem::getRouteDayId,
                        Collectors.mapping(this::toItemVO, Collectors.toList())));
        return days.stream()
                .map(d -> {
                    RouteDayVO vo = new RouteDayVO();
                    vo.setId(d.getId());
                    vo.setDayIndex(d.getDayIndex());
                    vo.setTitle(d.getTitle());
                    vo.setSummary(d.getSummary());
                    vo.setItems(itemMap.getOrDefault(d.getId(), Collections.emptyList()));
                    return vo;
                })
                .toList();
    }

    private RouteItemVO toItemVO(RouteItem item) {
        RouteItemVO vo = new RouteItemVO();
        vo.setId(item.getId());
        vo.setSortNo(item.getSortNo());
        vo.setTimePoint(item.getTimePoint());
        vo.setAttractionId(item.getAttractionId());
        vo.setTitle(item.getTitle());
        vo.setActivity(item.getActivity());
        vo.setTransport(item.getTransport());
        vo.setHotel(item.getHotel());
        vo.setMeal(item.getMeal());
        vo.setDurationMin(item.getDurationMin());
        vo.setCost(item.getCost());
        vo.setTips(item.getTips());
        return vo;
    }
}
