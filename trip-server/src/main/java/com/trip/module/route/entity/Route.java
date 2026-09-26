package com.trip.module.route.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 路线表 route（8 张逻辑删除表之一）。
 */
@Data
@TableName("route")
public class Route {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String subtitle;

    private String coverImg;

    private Long destinationId;

    /** 天数（= route_day 条数，BR-RTE-01） */
    private Integer days;

    private BigDecimal price;

    /** 难度 1-5（BR-RTE-03） */
    private Integer difficulty;

    /** 标签，逗号分隔 */
    private String tags;

    private String highlights;

    private String notice;

    private Integer likeCount;

    private Integer favoriteCount;

    private Integer bookingCount;

    private Integer commentCount;

    private Integer viewCount;

    private BigDecimal avgScore;

    /** 人工推荐权重 0-1（BR-RTE-05） */
    private BigDecimal recommendWeight;

    private Integer isTop;

    /** 每日预约名额上限（B4） */
    private Integer quotaPerDay;

    /** 1 上架 / 0 下架 */
    private Integer status;

    private Long createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}