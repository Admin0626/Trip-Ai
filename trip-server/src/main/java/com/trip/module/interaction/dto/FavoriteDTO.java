package com.trip.module.interaction.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 收藏 / 取消收藏请求（api/03 · POST /interaction/favorite）
 */
@Data
public class FavoriteDTO {

    @NotNull(message = "路线ID不能为空")
    @Positive(message = "路线ID非法")
    private Long routeId;
}