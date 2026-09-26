package com.trip.module.interaction.entity;

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
 * 路线评论表 route_comment（8 张逻辑删除表之一）。
 * status：1 显示 / 0 隐藏（管理员）；deleted：用户自删（与隐藏区分）
 */
@Data
@TableName("route_comment")
public class RouteComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long routeId;

    private Long userId;

    /** 父评论ID（0=顶级，回复最多 2 层） */
    private Long parentId;

    /** 评分 1-5 整数（BR-INT-05） */
    private Integer score;

    /** 评论内容 5-500 字 */
    private String content;

    /** 图片，逗号分隔，≤3 张 */
    private String images;

    private Integer likeCount;

    /** positive/neutral/negative/unknown（第 3 批 AI 填充） */
    private String sentiment;

    private BigDecimal sentimentScore;

    /** 情感分析失败重试次数（第 3 批使用） */
    private Integer sentimentRetry;

    /** AI 抽取关键词 */
    private String keywords;

    /** 1 显示 / 0 隐藏 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}