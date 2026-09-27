package com.trip.module.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Administrator request bodies. Identity, audit fields and counters never bind from input. */
public final class CatalogSaveDTO {
    private CatalogSaveDTO() {}

    @Data
    public static class DestinationInput {
        @NotBlank @Size(max=100) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String name;
        @NotBlank @Size(max=50) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String province;
        @Size(max=50) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String city = "";
        @NotNull @DecimalMin("73") @DecimalMax("136") @JsonDeserialize(using=StrictCatalogJson.DecimalNumber.class) private BigDecimal longitude;
        @NotNull @DecimalMin("3") @DecimalMax("54") @JsonDeserialize(using=StrictCatalogJson.DecimalNumber.class) private BigDecimal latitude;
        @NotBlank @Size(max=255) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String coverImg;
        @Size(max=10000) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String intro = "";
        @Size(max=5) @JsonDeserialize(contentUsing=StrictCatalogJson.Text.class) private List<@NotBlank @Size(max=30) String> tags = List.of();
        @Size(max=50) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String bestSeason = "";
        @NotNull @DecimalMin("0") @DecimalMax("99999999.99") @Digits(integer=8,fraction=2) @JsonDeserialize(using=StrictCatalogJson.DecimalNumber.class) private BigDecimal avgCost = BigDecimal.ZERO;
        @NotNull @Min(0) @Max(1) @JsonDeserialize(using=StrictCatalogJson.IntegerNumber.class) private Integer status = 0;
    }

    @Data
    public static class AttractionInput {
        @NotBlank @Size(max=100) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String name;
        @Size(max=255) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String coverImg = "";
        @Size(max=10000) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String intro = "";
        @Size(max=255) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String address = "";
        @NotNull @DecimalMin("-180") @DecimalMax("180") @JsonDeserialize(using=StrictCatalogJson.DecimalNumber.class) private BigDecimal longitude = BigDecimal.ZERO;
        @NotNull @DecimalMin("-90") @DecimalMax("90") @JsonDeserialize(using=StrictCatalogJson.DecimalNumber.class) private BigDecimal latitude = BigDecimal.ZERO;
        @NotNull @DecimalMin("0") @DecimalMax("99999999.99") @Digits(integer=8,fraction=2) @JsonDeserialize(using=StrictCatalogJson.DecimalNumber.class) private BigDecimal ticketPrice = BigDecimal.ZERO;
        @Size(max=100) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String openTime = "";
        @NotNull @Min(0) @Max(1440) @JsonDeserialize(using=StrictCatalogJson.IntegerNumber.class) private Integer durationMin = 0;
        @Size(max=5) @JsonDeserialize(contentUsing=StrictCatalogJson.Text.class) private List<@NotBlank @Size(max=30) String> tags = List.of();
        @NotNull @Min(0) @Max(1) @JsonDeserialize(using=StrictCatalogJson.IntegerNumber.class) private Integer status = 1;
    }

    @Data
    public static class RouteInput {
        @NotBlank @Size(max=200) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String title;
        @Size(max=255) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String subtitle = "";
        @NotBlank @Size(max=255) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String coverImg;
        @NotNull @Positive @JsonDeserialize(using=StrictCatalogJson.LongNumber.class) private Long destinationId;
        @NotNull @Min(1) @Max(30) @JsonDeserialize(using=StrictCatalogJson.IntegerNumber.class) private Integer days;
        @NotNull @DecimalMin("0") @DecimalMax("99999999.99") @Digits(integer=8,fraction=2) @JsonDeserialize(using=StrictCatalogJson.DecimalNumber.class) private BigDecimal price;
        @NotNull @Min(1) @Max(5) @JsonDeserialize(using=StrictCatalogJson.IntegerNumber.class) private Integer difficulty = 1;
        @Size(max=5) @JsonDeserialize(contentUsing=StrictCatalogJson.Text.class) private List<@NotBlank @Size(max=30) String> tags = List.of();
        @Size(max=10000) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String highlights = "";
        @Size(max=10000) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String notice = "";
        @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer=1,fraction=4) @JsonDeserialize(using=StrictCatalogJson.DecimalNumber.class) private BigDecimal recommendWeight = new BigDecimal("0.5");
        @NotNull @Min(0) @Max(1) @JsonDeserialize(using=StrictCatalogJson.IntegerNumber.class) private Integer isTop = 0;
        @NotNull @Min(1) @Max(10000) @JsonDeserialize(using=StrictCatalogJson.IntegerNumber.class) private Integer quotaPerDay = 20;
        @NotNull @Min(0) @Max(1) @JsonDeserialize(using=StrictCatalogJson.IntegerNumber.class) private Integer status = 0;
        @NotNull @Size(min=1,max=30) @Valid private List<@NotNull DayInput> dayList;
    }

    @Data
    public static class DayInput {
        @JsonDeserialize(using=StrictCatalogJson.IntegerNumber.class) private Integer dayIndex;
        @NotBlank @Size(max=100) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String title;
        @Size(max=255) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String summary = "";
        @NotNull @Size(min=1,max=15) @Valid private List<@NotNull ItemInput> items;
    }

    @Data
    public static class ItemInput {
        @JsonDeserialize(using=StrictCatalogJson.IntegerNumber.class) private Integer sortNo;
        @Pattern(regexp="^$|^([01][0-9]|2[0-3]):[0-5][0-9]$",message="时间点格式应为 HH:mm") @JsonDeserialize(using=StrictCatalogJson.Text.class) private String timePoint = "";
        @NotNull @Min(0) @JsonDeserialize(using=StrictCatalogJson.LongNumber.class) private Long attractionId = 0L;
        @NotBlank @Size(max=100) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String title;
        @Size(max=255) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String activity = "";
        @Size(max=50) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String transport = "";
        @Size(max=100) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String hotel = "";
        @Size(max=100) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String meal = "";
        @NotNull @Min(0) @Max(1440) @JsonDeserialize(using=StrictCatalogJson.IntegerNumber.class) private Integer durationMin = 0;
        @NotNull @DecimalMin("0") @DecimalMax("99999999.99") @Digits(integer=8,fraction=2) @JsonDeserialize(using=StrictCatalogJson.DecimalNumber.class) private BigDecimal cost = BigDecimal.ZERO;
        @Size(max=255) @JsonDeserialize(using=StrictCatalogJson.Text.class) private String tips = "";
    }

    public record StatusInput(@NotNull @Min(0) @Max(1) @JsonDeserialize(using=StrictCatalogJson.IntegerNumber.class) Integer status) {}
    public record TopInput(@NotNull @Min(0) @Max(1) @JsonDeserialize(using=StrictCatalogJson.IntegerNumber.class) Integer isTop) {}
    public record WeightInput(@NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer=1,fraction=4) @JsonDeserialize(using=StrictCatalogJson.DecimalNumber.class) BigDecimal recommendWeight) {}
}
