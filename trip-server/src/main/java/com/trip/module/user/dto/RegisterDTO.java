package com.trip.module.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求（api/01 · POST /auth/register）
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 20, message = "用户名长度 4-20 位")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名仅允许字母、数字、下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度 8-20 位")
    private String password;

    @Size(max = 50, message = "昵称最长 50 字")
    private String nickname;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @tools.jackson.databind.annotation.JsonDeserialize(using=com.trip.module.catalog.StrictCatalogJson.Text.class)
    @Size(max = 100, message = "邮箱最长100字符")
    private String email;

    /** 填写邮箱时必需；不填写邮箱仍可注册，之后在个人中心验证。 */
    @Size(max = 6, message = "邮箱验证码为6位数字")
    @tools.jackson.databind.annotation.JsonDeserialize(using=com.trip.module.catalog.StrictCatalogJson.Text.class)
    private String emailCode;
}
