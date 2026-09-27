package com.trip.module.user.controller;

import com.trip.common.result.R;
import com.trip.module.user.service.UserSettingsService;
import com.trip.module.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserSettingsService settings;

    @GetMapping("/profile")
    public R<UserVO> profile(@AuthenticationPrincipal Long userId) { return R.ok(settings.profile(userId)); }

    @PutMapping("/profile")
    public R<UserVO> updateProfile(@AuthenticationPrincipal Long userId, @RequestBody Map<String,Object> body) {
        return R.ok(settings.updateProfile(userId, body));
    }

    @PutMapping("/password")
    public R<Void> password(@AuthenticationPrincipal Long userId, @RequestBody Map<String,Object> body) {
        settings.password(userId, body); return R.ok();
    }

    @GetMapping("/preference")
    public R<UserSettingsService.Preference> preference(@AuthenticationPrincipal Long userId) { return R.ok(settings.preference(userId)); }

    @PutMapping("/preference")
    public R<UserSettingsService.Preference> updatePreference(@AuthenticationPrincipal Long userId, @RequestBody Map<String,Object> body) {
        return R.ok(settings.updatePreference(userId, body));
    }

    @GetMapping("/stats")
    public R<Map<String,Long>> stats(@AuthenticationPrincipal Long userId) { return R.ok(settings.stats(userId)); }
}
