package com.trip.module.route.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 路线列表项 VO（api/02 · GET /route/page records 元素）
 */
@Data
public class RoutePageVO {

    private Long id;
    private String title;
    private String subtitle;
    private String coverImg;
    private String destinationName;
    private Integer days;
    private BigDecimal price;
    private Integer difficulty;
    private List<String> tags;
    private Integer likeCount;
    private Integer favoriteCount;
    private Integer bookingCount;
    private Integer commentCount;
    private Integer viewCount;
    private BigDecimal avgScore;
    private Integer isTop;
    /** 当前用户是否点赞/收藏（互动模块尚未接入，先置 false） */
    private Boolean liked;
    private Boolean favorited;

    public static List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}