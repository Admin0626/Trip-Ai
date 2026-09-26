package com.trip.module.interaction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 路线点赞表 route_like（幂等开关，取消即物理删除，无 deleted / update_time）。
 */
@Data
@TableName("route_like")
public class RouteLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long routeId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}