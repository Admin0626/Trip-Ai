package com.trip.module.route.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 路线行程天 route_day。
 */
@Data
@TableName("route_day")
public class RouteDay {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long routeId;

    /** 第几天（从 1 起） */
    private Integer dayIndex;

    private String title;

    private String summary;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}