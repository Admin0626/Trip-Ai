package com.trip.module.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 规划行程项 user_plan_item（与 route_item 对称，无逻辑删除，随天整体替换）。
 */
@Data
@TableName("user_plan_item")
public class UserPlanItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planDayId;

    /** 天内排序号（服务端按数组顺序重排，BR-PLN-03） */
    private Integer sortNo;

    /** 时间点，如 09:00 */
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