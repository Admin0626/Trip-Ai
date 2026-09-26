package com.trip.module.plan.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 规划保存请求（api/03 · POST /plan 与 PUT /plan/{id} 共用）。
 * 服务端按 dayList 数组顺序整体替换重排，前端传的 sortNo/dayIndex 仅作参考。
 */
@Data
public class PlanSaveDTO {

    @NotBlank(message = "规划标题不能为空")
    @Size(max = 200, message = "规划标题最长 200 字")
    private String title;

    @NotNull(message = "目的地不能为空")
    private List<Long> destinationIds;

    /** 出发日期（≥今天，BR-PLN-02） */
    @NotNull(message = "出发日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private LocalDate startDate;

    /** 天数 1-30（BR-PLN-01） */
    @NotNull(message = "规划天数不能为空")
    @Min(value = 1, message = "规划天数 1-30 天")
    @Max(value = 30, message = "规划天数 1-30 天")
    private Integer days;

    /** 预算 > 0（BR-PLN-02） */
    @NotNull(message = "预算不能为空")
    @DecimalMin(value = "0.01", message = "预算必须大于 0")
    private BigDecimal budget;

    /** 同行人数 1-10 */
    @NotNull(message = "出行人数不能为空")
    @Min(value = 1, message = "出行人数 1-10 人")
    @Max(value = 10, message = "出行人数 1-10 人")
    private Integer peopleNum;

    /** 0 草稿 / 1 正式保存 */
    @Min(0)
    @Max(1)
    private Integer status;

    @Valid
    @Size(max = 30)
    private List<@NotNull DayDTO> dayList;

    /** 单天行程（内含 item 列表） */
    @Data
    public static class DayDTO {

        /** 第几天（服务端按数组顺序重排，忽略前端值） */
        private Integer dayIndex;

        @Size(max = 100)
        private String title;

        @Size(max = 255)
        private String summary;

        @Valid
        private List<@NotNull PlanItemDTO> items;
    }
}
