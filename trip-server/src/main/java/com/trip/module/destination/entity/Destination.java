package com.trip.module.destination.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 目的地表 destination（8 张逻辑删除表之一）。
 */
@Data
@TableName("destination")
public class Destination {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 目的地名称（同省内唯一） */
    private String name;

    private String province;

    private String city;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String coverImg;

    private String intro;

    /** 标签，逗号分隔，≤5 个 */
    private String tags;

    private String bestSeason;

    private BigDecimal avgCost;

    private Integer heat;

    /** 关联在架路线数（冗余，B9） */
    private Integer routeCount;

    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}