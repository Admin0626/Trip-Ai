package com.trip.module.interaction.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预约 VO（api/03 · POST /interaction/booking 响应 + GET /interaction/booking/page 列表元素）
 */
@Data
public class BookingVO {

    private Long bookingId;

    private String bookingNo;

    private Long routeId;

    /** 路线标题（列表页展示用，来自 route 表 join） */
    private String routeTitle;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private LocalDate travelDate;

    private Integer peopleNum;

    private String contactName;

    private String contactPhone;

    private String remark;

    /** 0 待确认 / 1 已确认 / 2 已取消 / 3 已完成 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}