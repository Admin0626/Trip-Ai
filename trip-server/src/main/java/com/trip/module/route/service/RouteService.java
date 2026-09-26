package com.trip.module.route.service;

import com.trip.common.result.PageResult;
import com.trip.module.route.vo.RouteDayVO;
import com.trip.module.route.vo.RouteDetailVO;
import com.trip.module.route.vo.RoutePageVO;

import java.math.BigDecimal;
import java.util.List;

public interface RouteService {

    PageResult<RoutePageVO> page(long current, long size, String keyword, Long destinationId,
                                 Integer days, BigDecimal priceMin, BigDecimal priceMax,
                                 Integer difficulty, String tag, String sortBy, Integer status);

    RouteDetailVO detail(Long id);

    List<RouteDayVO> days(Long id);

    List<RoutePageVO> hot(int limit);

    List<RoutePageVO> recommendHome();

    List<String> searchSuggest(String keyword);
}