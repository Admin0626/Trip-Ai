package com.trip.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trip.common.exception.BizException;
import com.trip.common.result.ResultCode;
import com.trip.common.util.JwtUtil;
import com.trip.module.user.dto.LoginDTO;
import com.trip.module.user.dto.RegisterDTO;
import com.trip.module.user.entity.SysUser;
import com.trip.module.user.mapper.SysUserMapper;
import com.trip.module.user.service.UserService;
import com.trip.module.user.service.EmailCodeService;
import com.trip.module.user.vo.LoginVO;
import com.trip.module.user.vo.UserVO;
import com.trip.security.AuthSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 认证与用户服务。
 * 规则依据 docs/08 §3：BR-USR-01 用户名 / BR-USR-02 密码 / BR-USR-03 邮箱手机号唯一。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{4,20}$");

    private final SysUserMapper sysUserMapper;
    private final AuthSessionService sessions;
    private final PasswordEncoder passwordEncoder;
    private final EmailCodeService emailCodes;

    @Override
    @Transactional
    public LoginVO login(LoginDTO dto) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()).last("FOR UPDATE"));
        if (user == null) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }

        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginTime(LocalDateTime.now());
        sysUserMapper.updateById(update);

        return sessions.create(user);
    }

    @Override
    @Transactional
    public UserVO register(RegisterDTO dto) {
        String username = dto.getUsername().trim();
        String password = dto.getPassword();
        if (isNotEmpty(dto.getEmail())) dto.setEmail(EmailCodeService.email(dto.getEmail()));
        else if (isNotEmpty(dto.getEmailCode())) throw new BizException(400,"请填写需要验证的邮箱");
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new BizException(400, "用户名 4-20 位，仅允许字母、数字、下划线");
        }
        if (password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit)) {
            throw new BizException(400, "密码须同时包含字母与数字");
        }
        if (findByName(username) != null) {
            throw new BizException(409, "用户名已存在");
        }
        if (isNotEmpty(dto.getPhone())
                && sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, dto.getPhone())) > 0) {
            throw new BizException(409, "手机号已注册");
        }
        if (isNotEmpty(dto.getEmail())
                && sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, dto.getEmail())) > 0) {
            throw new BizException(409, "邮箱已注册");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(isNotEmpty(dto.getNickname()) ? dto.getNickname() : username);
        user.setPhone(blankToNull(dto.getPhone()));
        user.setEmail(blankToNull(dto.getEmail()));
        user.setCity("");
        user.setRole("USER");
        user.setStatus(1);
        user.setEmailVerified(0);
        if (isNotEmpty(dto.getEmail())) {
            emailCodes.consume("REGISTER",dto.getEmail(),null,"REGISTER",dto.getEmailCode());
            user.setEmailVerified(1);
        }
        try { sysUserMapper.insert(user); }
        catch (org.springframework.dao.DuplicateKeyException e) { throw new BizException(409,"用户名、手机号或邮箱已被使用，请重新获取验证码"); }
        return UserVO.from(user);
    }

    @Override
    public UserVO getCurrentUser(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return UserVO.from(user);
    }

    private SysUser findByName(String username) {
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
    }

    private boolean isNotEmpty(String s) {
        return s != null && !s.isBlank();
    }

    private String blankToNull(String s) {
        return isNotEmpty(s) ? s : null;
    }
}
