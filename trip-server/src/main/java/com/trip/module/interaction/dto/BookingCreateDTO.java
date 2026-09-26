package com.trip.module.interaction.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 提交预约请求（api/03 · POST /interaction/booking，BR-INT-02/03/04）
 */
@Data
public class BookingCreateDTO {

    @NotNull(message = "路线ID不能为空")
    @Positive(message = "路线ID非法")
    private Long routeId;

    @NotNull(message = "出行日期不能为空")
    private LocalDate travelDate;

    @NotNull(message = "人数不能为空")
    @Min(value = 1, message = "人数 1-10 人")
    @Max(value = 10, message = "人数 1-10 人")
    private Integer peopleNum;

    @NotBlank(message = "联系人不能为空")
    @Size(max = 50, message = "联系人过长")
    private String contactName;

    @NotBlank(message = "联系电话不能为空")
    @Size(max = 20, message = "联系电话过长")
    private String contactPhone;

    @Size(max = 255, message = "备注过长")
    private String remark;
}