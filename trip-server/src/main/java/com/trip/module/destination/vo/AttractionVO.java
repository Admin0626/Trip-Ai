package com.trip.module.destination.vo;

import com.trip.module.destination.entity.Attraction;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 景点 VO（api/02 · GET /destination/{id}/attractions）
 */
@Data
public class AttractionVO {

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
    private List<String> tags;
    private Integer status;

    public static AttractionVO from(Attraction a) {
        AttractionVO vo = new AttractionVO();
        vo.setId(a.getId());
        vo.setDestinationId(a.getDestinationId());
        vo.setName(a.getName());
        vo.setCoverImg(a.getCoverImg());
        vo.setIntro(a.getIntro());
        vo.setAddress(a.getAddress());
        vo.setLongitude(a.getLongitude());
        vo.setLatitude(a.getLatitude());
        vo.setTicketPrice(a.getTicketPrice());
        vo.setOpenTime(a.getOpenTime());
        vo.setDurationMin(a.getDurationMin());
        vo.setTags(splitTags(a.getTags()));
        vo.setStatus(a.getStatus());
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