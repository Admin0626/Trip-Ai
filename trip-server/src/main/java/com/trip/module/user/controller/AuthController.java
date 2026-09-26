package com.trip.module.user.controller;

import com.trip.common.result.R;
import com.trip.common.result.ResultCode;
import com.trip.module.user.dto.LoginDTO;
import com.trip.module.user.dto.RegisterDTO;
import com.trip.module.user.service.UserService;
import com.trip.module.user.vo.LoginVO;
import com.trip.module.user.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（api/01 · /auth/**）
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public R<UserVO> register(@Valid @RequestBody RegisterDTO dto) {
        return R.ok(userService.register(dto));
    }

    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok(userService.login(dto));
    }

    /** 当前登录用户信息；未登录（principal 非 userId）时返回 401 */
    @GetMapping("/me")
    public R<UserVO> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long)) {
            return R.fail(ResultCode.UNAUTHORIZED);
        }
        Long userId = (Long) auth.getPrincipal();
        return R.ok(userService.getCurrentUser(userId));
    }
}