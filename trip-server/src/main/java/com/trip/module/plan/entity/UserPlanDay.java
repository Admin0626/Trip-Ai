package com.trip.module.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规划行程天 user_plan_day（与 route_day 对称，无逻辑删除，随主表整体替换）。
 */
@Data
@TableName("user_plan_day")
public class UserPlanDay {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userPlanId;

    /** 第几天（从 1 起，uk 与 user_plan_id 联合） */
    private Integer dayIndex;

    private String title;

    private String summary;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}