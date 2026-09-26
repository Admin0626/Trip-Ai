package com.trip.module.route.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 路线行程项 route_item（文档里曾写 RoutePlace，表名以 schema 为准）。
 */
@Data
@TableName("route_item")
public class RouteItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long routeDayId;

    /** 天内排序号（唯一且连续，BR-RTE-02 每天 1-15 条） */
    private Integer sortNo;

    /** 时间点，如 09:00（可选） */
    private String timePoint;

    /** 关联景点ID（0=非景点项） */
    private Long attractionId;

    private String title;

    private String activity;

    private String transport;

    private String hotel;

    private String meal;

    private Integer durationMin;

    private BigDecimal cost;

    private String tips;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}