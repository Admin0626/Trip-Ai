package com.trip.module.interaction.vo;

import lombok.Data;

/**
 * 点赞开关响应（api/03 · POST /interaction/like）
 */
@Data
public class LikeVO {

    /** 操作后是否处于点赞状态 */
    private Boolean liked;

    /** 该路线实时点赞数（取 route.like_count 冗余列） */
    private Integer likeCount;
}