package com.trip.module.interaction.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理端审核预约状态请求（api/03 · PUT /admin/interaction/booking/{id}/status）
 * 状态机：0 待确认 → 1 已确认 / 2 已取消；1 → 3 已完成（08 §2.3）
 */
@Data
public class BookingStatusDTO {

    @NotNull(message = "审核状态不能为空")
    @Min(value = 1, message = "仅支持 1 已确认 / 2 已取消 / 3 已完成")
    @Max(value = 3, message = "仅支持 1 已确认 / 2 已取消 / 3 已完成")
    @tools.jackson.databind.annotation.JsonDeserialize(using=com.trip.module.catalog.StrictCatalogJson.IntegerNumber.class)
    private Integer status;
}
