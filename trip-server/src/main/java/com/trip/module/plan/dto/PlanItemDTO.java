package com.trip.module.plan.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 规划单条行程项请求（api/03 · dayList.items 元素）。
 * sortNo 以数组顺序为准，前端传值仅作参考。
 */
@Data
public class PlanItemDTO {

    private Integer sortNo;

    /** 时间点，如 09:00 */
    @Size(max = 10)
    private String timePoint;

    /** 关联景点ID（0=非景点项） */
    private Long attractionId;

    @NotBlank(message = "行程项标题不能为空")
    @Size(max = 100, message = "行程项标题最长 100 字")
    private String title;

    @Size(max = 255, message = "活动内容最长 255 字")
    private String activity;

    @Size(max = 50, message = "交通方式最长 50 字")
    private String transport;

    @Size(max = 100, message = "住宿最长 100 字")
    private String hotel;

    @Size(max = 100, message = "用餐最长 100 字")
    private String meal;

    @Min(value = 0, message = "时长不能为负")
    private Integer durationMin;

    @DecimalMin(value = "0", message = "费用不能为负")
    private BigDecimal cost;

    @Size(max = 255, message = "小贴士最长 255 字")
    private String tips;
}
