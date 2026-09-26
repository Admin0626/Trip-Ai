package com.trip.module.interaction.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 点赞 / 取消点赞请求（api/03 · POST /interaction/like）
 */
@Data
public class LikeDTO {

    @NotNull(message = "路线ID不能为空")
    @Positive(message = "路线ID非法")
    private Long routeId;
}