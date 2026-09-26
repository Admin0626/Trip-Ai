package com.trip.module.interaction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 路线收藏表 route_favorite（幂等开关，取消即物理删除，无 deleted / update_time）。
 */
@Data
@TableName("route_favorite")
public class RouteFavorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long routeId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}