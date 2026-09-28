package com.trip.module.interaction.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理端显示 / 隐藏评论请求（api/03 · PUT /admin/interaction/comment/{id}/status）
 */
@Data
public class CommentStatusDTO {

    @NotNull(message = "评论状态不能为空")
    @Min(value = 0, message = "评论状态仅支持 0 隐藏 / 1 显示")
    @Max(value = 1, message = "评论状态仅支持 0 隐藏 / 1 显示")
    @tools.jackson.databind.annotation.JsonDeserialize(using=com.trip.module.catalog.StrictCatalogJson.IntegerNumber.class)
    private Integer status;
}
