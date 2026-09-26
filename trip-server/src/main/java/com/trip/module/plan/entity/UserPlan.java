package com.trip.module.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户自主规划 user_plan（8 张逻辑删除表之一）。
 */
@Data
@TableName("user_plan")
public class UserPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    /** 目的地ID，逗号分隔 */
    private String destinationIds;

    /** 出发日期（≥今天，BR-PLN-02） */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private LocalDate startDate;

    /** 天数 1-30（BR-PLN-01） */
    private Integer days;

    /** 预算（>0，BR-PLN-02） */
    private BigDecimal budget;

    private Integer peopleNum;

    /** 0 草稿 / 1 已保存（BR-PLN-05 草稿不参与统计） */
    private Integer status;

    /** 来源路线ID（模板创建时记录） */
    private Long sourceRouteId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}