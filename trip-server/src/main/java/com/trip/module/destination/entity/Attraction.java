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
 * 景点表 attraction（行程项素材来源）。
 */
@Data
@TableName("attraction")
public class Attraction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long destinationId;

    private String name;

    private String coverImg;

    private String intro;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private BigDecimal ticketPrice;

    private String openTime;

    private Integer durationMin;

    /** 标签，逗号分隔 */
    private String tags;

    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}