package com.trip.module.interaction.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论 VO（api/03 · POST /interaction/comment 响应 + GET /interaction/comment/page 列表元素）
 */
@Data
public class CommentVO {

    private Long id;

    private Long routeId;

    private Long userId;

    /** 用户昵称（列表页展示用，来自 sys_user join） */
    private String userNickname;

    /** 用户头像 */
    private String userAvatar;

    /** 父评论ID（0=顶级） */
    private Long parentId;

    /** 评分 1-5 */
    private Integer score;

    /** 0 hidden / 1 visible, used by administrator moderation. */
    private Integer status;

    private String content;

    /** 图片 URL 列表（实体里逗号分隔，VO 转数组） */
    private List<String> images;

    private Integer likeCount;

    /** 当前登录用户是否点过赞（未登录默认 false） */
    private Boolean liked;

    /** positive/neutral/negative/unknown（第 3 批 AI 填充） */
    private String sentiment;

    private BigDecimal sentimentScore;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
