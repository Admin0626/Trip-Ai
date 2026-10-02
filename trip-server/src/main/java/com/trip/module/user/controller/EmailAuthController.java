package com.trip.module.user.controller;

import com.trip.common.result.R;
import com.trip.module.user.service.EmailAccountService;
import com.trip.module.user.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class EmailAuthController {
    private final EmailAccountService emails;
    @PostMapping("/auth/email/code")
    public R<Map<String,Object>> registration(@RequestBody Map<String,Object> body,HttpServletRequest request) {
        return R.ok(emails.registrationCode(body,request.getRemoteAddr()));
    }
    @PostMapping("/auth/password/code")
    public R<Map<String,Object>> recovery(@RequestBody Map<String,Object> body,HttpServletRequest request) {
        return R.ok(emails.resetCode(body,request.getRemoteAddr()));
    }
    @PostMapping("/auth/password/reset")
    public R<Void> reset(@RequestBody Map<String,Object> body) { emails.reset(body); return R.ok(); }
    @PostMapping("/user/email/code")
    public R<Map<String,Object>> binding(@AuthenticationPrincipal Long uid,@RequestBody Map<String,Object> body,HttpServletRequest request) {
        return R.ok(emails.bindingCode(uid,body,request.getRemoteAddr()));
    }
    @PostMapping("/user/email/verify")
    public R<UserVO> verify(@AuthenticationPrincipal Long uid,@RequestBody Map<String,Object> body) { return R.ok(emails.verify(uid,body)); }
}
