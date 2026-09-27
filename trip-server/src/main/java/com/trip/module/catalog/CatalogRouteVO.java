package com.trip.module.catalog;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CatalogRouteVO extends CatalogSaveDTO.RouteInput {
    private Long id;
    private String destinationName;
    private Integer bookingCount;
    private Integer viewCount;
}
