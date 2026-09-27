package com.trip.module.route.controller;

import com.trip.common.result.PageResult;
import com.trip.common.result.R;
import com.trip.module.route.service.RouteService;
import com.trip.module.route.vo.RouteDayVO;
import com.trip.module.route.vo.RouteDetailVO;
import com.trip.module.route.vo.RoutePageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 路线接口（api/02 · GET /route/** 游客可读）
 */
@RestController
@RequestMapping("/route")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @GetMapping("/page")
    public R<PageResult<RoutePageVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long destinationId,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) BigDecimal priceMin,
            @RequestParam(required = false) BigDecimal priceMax,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) Integer status) {
        size = Math.min(size, 100);
        return R.ok(routeService.page(current, size, keyword, destinationId, days,
                priceMin, priceMax, difficulty, tag, sortBy, 1));
    }

    @GetMapping("/{id}")
    public R<RouteDetailVO> detail(@PathVariable Long id) {
        return R.ok(routeService.detail(id));
    }

    @GetMapping("/{id}/days")
    public R<List<RouteDayVO>> days(@PathVariable Long id) {
        return R.ok(routeService.days(id));
    }

    @GetMapping("/hot")
    public R<List<RoutePageVO>> hot(@RequestParam(defaultValue = "5") int limit) {
        return R.ok(routeService.hot(limit));
    }

    @GetMapping("/recommend/home")
    public R<List<RoutePageVO>> recommendHome() {
        return R.ok(routeService.recommendHome());
    }

    @GetMapping("/search/suggest")
    public R<List<String>> searchSuggest(@RequestParam String keyword) {
        return R.ok(routeService.searchSuggest(keyword));
    }
}
