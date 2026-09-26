package com.trip.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求（api/01 · POST /auth/login）
 */
@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 20, message = "用户名长度 4-20 位")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 50, message = "密码过长")
    private String password;
}