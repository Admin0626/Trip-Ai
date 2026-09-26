package com.trip.module.interaction.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发表评论请求（api/03 · POST /interaction/comment，BR-INT-05/06/07）
 */
@Data
public class CommentCreateDTO {

    @NotNull(message = "路线ID不能为空")
    @Positive(message = "路线ID非法")
    private Long routeId;

    /** 父评论ID（0=顶级，仅允许回复一层） */
    @Min(value = 0, message = "父评论ID不能为负")
    private Long parentId;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分 1-5 星")
    @Max(value = 5, message = "评分 1-5 星")
    private Integer score;

    @NotBlank(message = "评论内容不能为空")
    @Size(min = 5, max = 500, message = "评论内容 5-500 字")
    private String content;

    /** 评论图片，最多 3 张 */
    @Size(max = 3, message = "评论图片最多 3 张")
    private List<String> images;
}
