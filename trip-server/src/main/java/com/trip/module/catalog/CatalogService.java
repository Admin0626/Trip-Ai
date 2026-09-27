package com.trip.module.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trip.common.exception.BizException;
import com.trip.common.result.PageResult;
import com.trip.module.destination.entity.*;
import com.trip.module.destination.mapper.*;
import com.trip.module.destination.vo.*;
import com.trip.module.route.entity.*;
import com.trip.module.route.mapper.*;
import com.trip.module.route.vo.RoutePageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CatalogService {
    private final DestinationMapper destinations;
    private final AttractionMapper attractions;
    private final RouteMapper routes;
    private final RouteDayMapper days;
    private final RouteItemMapper items;
    private final JdbcTemplate jdbc;

    public PageResult<DestinationVO> destinations(long current, long size, String keyword, Integer status) {
        pageBounds(current, size, status);
        var query = new LambdaQueryWrapper<Destination>().eq(status != null, Destination::getStatus, status)
                .like(keyword != null && !keyword.isBlank(), Destination::getName, keyword).orderByDesc(Destination::getId);
        var page = destinations.selectPage(new Page<>(current, size), query);
        return pageResult(page, page.getRecords().stream().map(DestinationVO::from).toList());
    }

    public DestinationVO destination(long id) { return DestinationVO.from(requireDestination(id, false)); }

    @Transactional
    public Long saveDestination(Long id, CatalogSaveDTO.DestinationInput input) {
        Destination value = id == null ? new Destination() : requireDestination(id, true);
        // The unique index also protects simultaneous inserts and tombstones.
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM destination WHERE province=? AND name=? AND id<>?",
                Long.class, input.getProvince().strip(), input.getName().strip(), id == null ? 0 : id);
        if (count != null && count > 0) throw new BizException(409, "同省份已存在该名称的目的地（含已删除记录）");
        BeanUtils.copyProperties(input, value, "tags");
        value.setName(input.getName().strip()); value.setProvince(input.getProvince().strip());
        value.setTags(tags(input.getTags())); value.setUpdateTime(LocalDateTime.now());
        try {
            if (id == null) {
                value.setDeleted(0); value.setHeat(0); value.setRouteCount(0); value.setCreateTime(LocalDateTime.now());
                destinations.insert(value);
            } else destinations.updateById(value);
        } catch (DuplicateKeyException e) { throw new BizException(409, "同省份已存在该名称的目的地"); }
        return value.getId();
    }

    @Transactional
    public void destinationStatus(long id, int status) {
        Destination value = requireDestination(id, true); value.setStatus(status); destinations.updateById(value);
    }

    @Transactional
    public void deleteDestination(long id) {
        requireDestination(id, true);
        if (routes.selectCount(new LambdaQueryWrapper<Route>().eq(Route::getDestinationId, id)) > 0)
            throw new BizException(409, "目的地仍有关联路线，请先处理路线");
        attractions.delete(new LambdaQueryWrapper<Attraction>().eq(Attraction::getDestinationId, id));
        destinations.deleteById(id);
    }

    public List<AttractionVO> attractions(long destinationId) {
        requireDestination(destinationId, false);
        return attractions.selectList(new LambdaQueryWrapper<Attraction>().eq(Attraction::getDestinationId, destinationId)
                .orderByAsc(Attraction::getId)).stream().map(AttractionVO::from).toList();
    }

    @Transactional
    public Long saveAttraction(Long destinationId, Long id, CatalogSaveDTO.AttractionInput input) {
        Attraction value = id == null ? new Attraction() : attractions.selectById(id);
        if (value == null) throw new BizException(404, "景点不存在");
        long parentId = id == null ? destinationId : value.getDestinationId();
        requireDestination(parentId, true);
        BeanUtils.copyProperties(input, value, "tags"); value.setDestinationId(parentId);
        value.setName(input.getName().strip()); value.setTags(tags(input.getTags())); value.setUpdateTime(LocalDateTime.now());
        if (id == null) { value.setDeleted(0); value.setCreateTime(LocalDateTime.now()); attractions.insert(value); }
        else attractions.updateById(value);
        return value.getId();
    }

    @Transactional
    public void deleteAttraction(long id) {
        Attraction value = attractions.selectById(id);
        if (value == null) throw new BizException(404, "景点不存在");
        requireDestination(value.getDestinationId(), true);
        attractions.deleteById(id); // Existing route and personal-plan items keep their saved text snapshots.
    }

    public PageResult<CatalogRouteVO> routes(long current, long size, String keyword, Integer status, Long destinationId) {
        pageBounds(current, size, status);
        var query = new LambdaQueryWrapper<Route>().eq(status != null, Route::getStatus, status)
                .eq(destinationId != null, Route::getDestinationId, destinationId)
                .like(keyword != null && !keyword.isBlank(), Route::getTitle, keyword).orderByDesc(Route::getId);
        var page = routes.selectPage(new Page<>(current, size), query);
        return pageResult(page, page.getRecords().stream().map(value -> routeVO(value, false)).toList());
    }

    @Transactional(readOnly=true)
    public CatalogRouteVO route(long id) { return routeVO(requireRoute(id, false), true); }

    @Transactional
    public Long saveRoute(Long id, long userId, CatalogSaveDTO.RouteInput input) {
        if (input.getDays() != input.getDayList().size()) throw new BizException(400, "路线天数必须等于行程天数");
        Route value = id == null ? new Route() : requireRoute(id, true);
        Long oldDestinationId = value.getDestinationId();
        // Lock the parent against deletion while writing; route readers use a consistent transaction snapshot.
        Destination parent = requireDestination(input.getDestinationId(), true);
        if (input.getStatus() == 1 && !Integer.valueOf(1).equals(parent.getStatus()))
            throw new BizException(409, "目的地未上架，路线不能上架");
        if (id != null && input.getQuotaPerDay() < value.getQuotaPerDay()) {
            Long active = jdbc.queryForObject("SELECT COUNT(*) FROM route_booking WHERE route_id=? AND status IN(0,1) AND travel_date>=CURRENT_DATE", Long.class, id);
            if (active != null && active > 0) throw new BizException(409, "存在待确认或已确认的未来预约，暂不能降低每日名额");
        }
        validateAttractions(input);
        BeanUtils.copyProperties(input, value, "tags", "dayList");
        value.setTitle(input.getTitle().strip()); value.setTags(tags(input.getTags())); value.setUpdateTime(LocalDateTime.now());
        if (id == null) {
            value.setDeleted(0); value.setCreateBy(userId); value.setCreateTime(LocalDateTime.now());
            value.setLikeCount(0); value.setFavoriteCount(0); value.setBookingCount(0); value.setCommentCount(0); value.setViewCount(0); value.setAvgScore(BigDecimal.ZERO);
            routes.insert(value);
        } else routes.updateById(value);
        replaceDays(value.getId(), input.getDayList());
        recount(input.getDestinationId());
        if (oldDestinationId != null && !oldDestinationId.equals(input.getDestinationId())) recount(oldDestinationId);
        return value.getId();
    }

    @Transactional
    public void routeStatus(long id, int status) {
        Route value = requireRoute(id, true);
        Destination parent = requireDestination(value.getDestinationId(), true);
        if (status == 1 && !Integer.valueOf(1).equals(parent.getStatus())) throw new BizException(409, "目的地未上架，路线不能上架");
        value.setStatus(status); routes.updateById(value); recount(value.getDestinationId());
    }

    @Transactional
    public void deleteRoute(long id) {
        Route value = requireRoute(id, true);
        Long booked = jdbc.queryForObject("SELECT COUNT(*) FROM route_booking WHERE route_id=?", Long.class, id);
        if (booked != null && booked > 0) throw new BizException(409, "已有预约记录的路线只能下架，不能删除");
        routes.deleteById(id); recount(value.getDestinationId());
    }

    @Transactional
    public void top(long id, int isTop) { Route value = requireRoute(id, true); value.setIsTop(isTop); routes.updateById(value); }

    @Transactional
    public void weight(long id, BigDecimal weight) { Route value = requireRoute(id, true); value.setRecommendWeight(weight); routes.updateById(value); }

    private void validateAttractions(CatalogSaveDTO.RouteInput input) {
        for (var day : input.getDayList()) for (var item : day.getItems()) {
            if (item.getAttractionId() == 0) continue;
            Attraction attraction = attractions.selectById(item.getAttractionId());
            if (attraction == null || !Integer.valueOf(1).equals(attraction.getStatus()) || !input.getDestinationId().equals(attraction.getDestinationId()))
                throw new BizException(400, "行程关联景点必须属于当前目的地且已上架");
        }
    }

    private void replaceDays(long routeId, List<CatalogSaveDTO.DayInput> dayList) {
        jdbc.update("DELETE ri FROM route_item ri JOIN route_day rd ON rd.id=ri.route_day_id WHERE rd.route_id=?", routeId);
        days.delete(new LambdaQueryWrapper<RouteDay>().eq(RouteDay::getRouteId, routeId));
        for (int d = 0; d < dayList.size(); d++) {
            var input = dayList.get(d); RouteDay day = new RouteDay();
            BeanUtils.copyProperties(input, day, "items", "dayIndex"); day.setRouteId(routeId); day.setDayIndex(d + 1); days.insert(day);
            for (int i = 0; i < input.getItems().size(); i++) {
                RouteItem item = new RouteItem(); BeanUtils.copyProperties(input.getItems().get(i), item, "sortNo");
                item.setRouteDayId(day.getId()); item.setSortNo(i + 1); items.insert(item);
            }
        }
    }

    private CatalogRouteVO routeVO(Route value, boolean includeDays) {
        CatalogRouteVO vo = new CatalogRouteVO(); BeanUtils.copyProperties(value, vo, "tags");
        vo.setTags(RoutePageVO.splitTags(value.getTags()));
        Destination parent = destinations.selectById(value.getDestinationId()); vo.setDestinationName(parent == null ? "" : parent.getName());
        if (includeDays) vo.setDayList(days.selectList(new LambdaQueryWrapper<RouteDay>().eq(RouteDay::getRouteId, value.getId()).orderByAsc(RouteDay::getDayIndex)).stream().map(day -> {
            var dto = new CatalogSaveDTO.DayInput(); BeanUtils.copyProperties(day, dto);
            dto.setItems(items.selectList(new LambdaQueryWrapper<RouteItem>().eq(RouteItem::getRouteDayId, day.getId()).orderByAsc(RouteItem::getSortNo)).stream().map(item -> {
                var data = new CatalogSaveDTO.ItemInput(); BeanUtils.copyProperties(item, data); return data;
            }).toList()); return dto;
        }).toList());
        return vo;
    }

    private Destination requireDestination(long id, boolean lock) {
        var query = new LambdaQueryWrapper<Destination>().eq(Destination::getId, id);
        if (lock) query.last("FOR UPDATE");
        Destination result = destinations.selectOne(query);
        if (result == null) throw new BizException(404, "目的地不存在"); return result;
    }

    private Route requireRoute(long id, boolean lock) {
        var query = new LambdaQueryWrapper<Route>().eq(Route::getId, id); if (lock) query.last("FOR UPDATE");
        Route result = routes.selectOne(query); if (result == null) throw new BizException(404, "路线不存在"); return result;
    }

    private void recount(long destinationId) {
        jdbc.update("UPDATE destination SET route_count=(SELECT COUNT(*) FROM route WHERE destination_id=? AND deleted=0 AND status=1) WHERE id=?", destinationId, destinationId);
    }

    private String tags(List<String> values) {
        if (values == null) return "";
        if (values.stream().anyMatch(value -> value.contains(",") || value.contains("，"))) throw new BizException(400, "单个标签不能包含逗号");
        return String.join(",", values.stream().map(String::strip).distinct().toList());
    }

    private void pageBounds(long current, long size, Integer status) {
        if (current < 1 || current > 1000000 || size < 1 || size > 100 || status != null && status != 0 && status != 1)
            throw new BizException(400, "分页或状态参数不正确");
    }

    private <T, S> PageResult<S> pageResult(Page<T> page, List<S> records) {
        var result = new PageResult<S>(); result.setRecords(records); result.setCurrent(page.getCurrent()); result.setSize(page.getSize());
        result.setTotal(page.getTotal()); result.setPages(page.getPages()); return result;
    }
}
