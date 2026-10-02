package com.trip.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.trip.common.exception.BizException;
import com.trip.module.user.entity.SysUser;
import com.trip.module.user.mapper.SysUserMapper;
import com.trip.module.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailAccountService {
    private final SysUserMapper users;
    private final EmailCodeService codes;
    private final PasswordEncoder passwords;

    public Map<String,Object> registrationCode(Map<String,Object> body,String ip) {
        EmailCodeService.fields(body,"email");
        String email=EmailCodeService.email(body.get("email"));
        String id=codes.reserve(email,ip);
        codes.send("REGISTER",email,null,"REGISTER",id);
        return receipt();
    }

    public Map<String,Object> resetCode(Map<String,Object> body,String ip) {
        EmailCodeService.fields(body,"email");
        String email=EmailCodeService.email(body.get("email"));
        String id=codes.reserve(email,ip);
        SysUser user=findEmail(email,false);
        String binding=eligible(user)?EmailCodeService.binding(user):"INELIGIBLE";
        codes.send("RESET",email,null,binding,id);
        return receipt();
    }

    public Map<String,Object> bindingCode(Long uid,Map<String,Object> body,String ip) {
        EmailCodeService.fields(body,"email","password");
        String email=EmailCodeService.email(body.get("email"));
        String password=EmailCodeService.text(body,"password",100);
        String id=codes.reserve(email,ip);
        SysUser user=active(uid,false);
        if (!passwords.matches(password,user.getPassword())) throw new BizException(400,"当前密码不正确");
        codes.send("BIND",email,uid,EmailCodeService.binding(user),id);
        return receipt();
    }

    @Transactional
    public UserVO verify(Long uid,Map<String,Object> body) {
        EmailCodeService.fields(body,"email","code");
        String email=EmailCodeService.email(body.get("email"));
        String code=EmailCodeService.text(body,"code",6);
        SysUser user=active(uid,true);
        // Check collision before consumption. DB unique key still protects racing confirmations.
        Long count=users.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail,email).ne(SysUser::getId,uid));
        if (count>0) throw new BizException(409,"邮箱已被使用");
        codes.consume("BIND",email,uid,EmailCodeService.binding(user),code);
        try {
            users.update(null,new LambdaUpdateWrapper<SysUser>().eq(SysUser::getId,uid)
                    .set(SysUser::getEmail,email).set(SysUser::getEmailVerified,1));
        } catch(DuplicateKeyException e) { throw new BizException(409,"邮箱已被使用，请重新获取验证码"); }
        return UserVO.from(users.selectById(uid));
    }

    @Transactional
    public void reset(Map<String,Object> body) {
        EmailCodeService.fields(body,"email","code","newPassword");
        String email=EmailCodeService.email(body.get("email"));
        String code=EmailCodeService.text(body,"code",6);
        String password=EmailCodeService.text(body,"newPassword",20);
        EmailCodeService.password(password);
        SysUser user=findEmail(email,true);
        String binding=eligible(user)?EmailCodeService.binding(user):"NO_VALID_ACCOUNT";
        codes.consume("RESET",email,null,binding,code);
        if (!eligible(user)) throw EmailCodeService.invalid();
        // Login, binding, reset and administrator disable all serialize on the same user row.
        if (passwords.matches(password,user.getPassword())) throw new BizException(400,"新密码不能与原密码相同，请重新获取验证码");
        users.update(null,new LambdaUpdateWrapper<SysUser>().eq(SysUser::getId,user.getId())
                .set(SysUser::getPassword,passwords.encode(password)));
        // AuthSessionService verifies the DB password fingerprint for both access and refresh tokens.
    }

    private SysUser active(Long id,boolean lock) {
        SysUser user=users.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getId,id).last(lock?"FOR UPDATE":""));
        if (user==null || !Integer.valueOf(1).equals(user.getStatus())) throw new BizException(401,"请重新登录");
        return user;
    }
    private SysUser findEmail(String email,boolean lock) {
        return users.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail,email).last(lock?"FOR UPDATE":""));
    }
    private boolean eligible(SysUser user) { return user!=null && Integer.valueOf(1).equals(user.getStatus()) && Integer.valueOf(1).equals(user.getEmailVerified()); }
    private Map<String,Object> receipt() { return Map.of("message","邮件已发送，请查看收件箱或垃圾邮件。找回密码仅适用于已验证邮箱的有效账号。","expiresIn",EmailCodeService.TTL,"retryAfter",EmailCodeService.COOLDOWN); }
}
