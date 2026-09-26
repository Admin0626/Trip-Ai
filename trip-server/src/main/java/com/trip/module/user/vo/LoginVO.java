package com.trip.module.user.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 登录响应（api/01 · POST /auth/login）
 */
@Data
@Builder
public class LoginVO {

    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private UserVO userInfo;
}