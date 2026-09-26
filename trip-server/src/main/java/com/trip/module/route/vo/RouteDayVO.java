package com.trip.module.route.vo;

import lombok.Data;

import java.util.List;

/**
 * 行程天 VO（api/02 · GET /route/{id} dayList 元素）
 */
@Data
public class RouteDayVO {

    private Long id;
    private Integer dayIndex;
    private String title;
    private String summary;
    private List<RouteItemVO> items;
}