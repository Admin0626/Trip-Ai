package com.trip.module.user.service;

import com.trip.common.exception.BizException;
import com.trip.security.AuthSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountClosureService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final AuthSessionService sessions;

    @Transactional
    public void close(long uid, Map<String,Object> body) {
        if(body==null || !body.keySet().equals(Set.of("password","confirmation"))
                || !(body.get("password") instanceof String password) || password.isBlank() || password.length()>100
                || !"注销账号".equals(body.get("confirmation"))) throw new BizException(400,"请填写当前密码并输入‘注销账号’确认");
        var rows=jdbc.queryForList("SELECT password,role,status,deleted FROM sys_user WHERE id=? FOR UPDATE",uid);
        if(rows.isEmpty() || ((Number)rows.get(0).get("deleted")).intValue()!=0 || ((Number)rows.get(0).get("status")).intValue()!=1)
            throw new BizException(401,"账号已不可用，请重新登录");
        var user=rows.get(0);
        if(!"USER".equals(user.get("role"))) throw new BizException(403,"管理员账号不能在此注销");
        if(!passwords.matches(password,(String)user.get("password"))) throw new BizException(400,"当前密码不正确");
        long active=jdbc.queryForObject("SELECT COUNT(*) FROM route_booking WHERE user_id=? AND status IN (0,1)",Long.class,uid);
        if(active>0)throw new BizException(409,"还有待确认或已确认预约，请先取消或完成预约");
        // Login and new bookings lock this same user row; no new session/booking can race closure.
        sessions.revokeAll(uid);
        jdbc.update("UPDATE sys_user SET deleted=1,status=0,password=?,nickname='已注销用户',avatar='',phone=NULL,email=NULL,email_verified=0,city='',update_time=NOW() WHERE id=?",
                passwords.encode(UUID.randomUUID().toString()),uid);
        jdbc.update("DELETE FROM user_preference WHERE user_id=?",uid);
        // Retain IDs and business history so existing counters/references are not corrupted.
    }
}
