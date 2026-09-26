package com.trip.module.interaction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 路线预约表 route_booking。
 * status：0 待确认 / 1 已确认 / 2 已取消 / 3 已完成（BR-INT-02/03/04）
 */
@Data
@TableName("route_booking")
public class RouteBooking {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 单号 BK+yyyyMMdd+3 位流水 */
    private String bookingNo;

    private Long routeId;

    private Long userId;

    /** 出行日期（≥ 明天） */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private LocalDate travelDate;

    /** 人数 1-10 */
    private Integer peopleNum;

    private String contactName;

    private String contactPhone;

    private String remark;

    private Integer status;

    /** 审核管理员 */
    private Long auditBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime auditTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime cancelTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}