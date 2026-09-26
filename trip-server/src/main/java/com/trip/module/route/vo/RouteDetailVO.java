package com.trip.module.route.vo;

import com.trip.module.destination.entity.Destination;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 路线详情 VO（api/02 · GET /route/{id}）
 */
@Data
public class RouteDetailVO {

    private Long id;
    private String title;
    private String subtitle;
    private String coverImg;
    private DestinationRef destination;
    private Integer days;
    private BigDecimal price;
    private Integer difficulty;
    private List<String> tags;
    private String highlights;
    private String notice;
    private Integer likeCount;
    private Integer favoriteCount;
    private Integer bookingCount;
    private Integer commentCount;
    private Integer viewCount;
    private BigDecimal avgScore;
    private Boolean liked;
    private Boolean favorited;
    private List<RouteDayVO> dayList;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DestinationRef {
        private Long id;
        private String name;
        private String province;

        public static DestinationRef from(Destination d) {
            if (d == null) {
                return null;
            }
            return new DestinationRef(d.getId(), d.getName(), d.getProvince());
        }
    }
}