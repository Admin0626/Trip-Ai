package com.trip.module.route.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 行程项 VO（api/02 · GET /route/{id} dayList[].items 元素）
 */
@Data
public class RouteItemVO {

    private Long id;
    private Integer sortNo;
    private String timePoint;
    private Long attractionId;
    private String title;
    private String activity;
    private String transport;
    private String hotel;
    private String meal;
    private Integer durationMin;
    private BigDecimal cost;
    private String tips;
}