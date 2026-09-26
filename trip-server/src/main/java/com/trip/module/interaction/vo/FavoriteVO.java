package com.trip.module.interaction.vo;

import lombok.Data;

/**
 * 收藏开关响应（api/03 · POST /interaction/favorite）
 */
@Data
public class FavoriteVO {

    /** 操作后是否处于收藏状态 */
    private Boolean favorited;

    /** 该路线实时收藏数（取 route.favorite_count 冗余列） */
    private Integer favoriteCount;
}