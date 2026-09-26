package com.trip.module.plan.vo;

import lombok.Data;

import java.util.List;

/**
 * 规划行程天 VO（api/03 · plan.dayList 元素）。
 */
@Data
public class PlanDayVO {

    private Long id;

    private Integer dayIndex;

    private String title;

    private String summary;

    private List<PlanItemVO> items;
}