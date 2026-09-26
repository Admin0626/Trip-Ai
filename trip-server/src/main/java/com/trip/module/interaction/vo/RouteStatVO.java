package com.trip.module.interaction.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 单条路线互动统计（api/03 · GET /admin/interaction/stat/{routeId}，INT-12）
 */
@Data
public class RouteStatVO {

    private Long routeId;

    private Integer likeCount;

    private Integer favoriteCount;

    private Integer bookingCount;

    private Integer commentCount;

    private Integer viewCount;

    /** 平均评分（route.avg_score 冗余列） */
    private BigDecimal avgScore;
}