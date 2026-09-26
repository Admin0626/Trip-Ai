package com.trip.module.destination.vo;

import com.trip.module.destination.entity.Destination;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 目的地 VO（api/02 · GET /destination/page records 元素）
 */
@Data
public class DestinationVO {

    private Long id;
    private String name;
    private String province;
    private String city;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String coverImg;
    private String intro;
    /** 标签数组（库中逗号分隔） */
    private List<String> tags;
    private String bestSeason;
    private BigDecimal avgCost;
    private Integer heat;
    private Integer routeCount;
    private Integer status;

    public static DestinationVO from(Destination d) {
        DestinationVO vo = new DestinationVO();
        vo.setId(d.getId());
        vo.setName(d.getName());
        vo.setProvince(d.getProvince());
        vo.setCity(d.getCity());
        vo.setLongitude(d.getLongitude());
        vo.setLatitude(d.getLatitude());
        vo.setCoverImg(d.getCoverImg());
        vo.setIntro(d.getIntro());
        vo.setTags(splitTags(d.getTags()));
        vo.setBestSeason(d.getBestSeason());
        vo.setAvgCost(d.getAvgCost());
        vo.setHeat(d.getHeat());
        vo.setRouteCount(d.getRouteCount());
        vo.setStatus(d.getStatus());
        return vo;
    }

    static List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}