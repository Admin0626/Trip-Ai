package com.trip.module.plan.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 规划行程项 VO（api/03 · plan.dayList.items 元素）。
 */
@Data
public class PlanItemVO {

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