package com.trip.module.plan.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 规划 VO（api/03 · GET /plan/page 列表元素 + GET /plan/{id} 详情）。
 */
@Data
public class PlanVO {

    private Long id;

    private String title;

    /** 目的地ID列表（保存时逗号拼接存储，返回时拆回） */
    private List<Long> destinationIds;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private LocalDate startDate;

    private Integer days;

    private BigDecimal budget;

    private Integer peopleNum;

    /** 0 草稿 / 1 已保存 */
    private Integer status;

    /** 来源路线ID（模板创建时记录，0=手写） */
    private Long sourceRouteId;

    /** 行程天数（列表页：哪天没填项前端能提示） */
    private Integer dayCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    /** 详情页返回完整行程（列表页为 null） */
    private List<PlanDayVO> dayList;
}