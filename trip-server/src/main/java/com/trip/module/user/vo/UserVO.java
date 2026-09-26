package com.trip.module.user.vo;

import com.trip.module.user.entity.SysUser;
import lombok.Data;

/**
 * 用户信息 VO（登录 userInfo、/auth/me）
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String role;
    private String city;

    public static UserVO from(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setCity(user.getCity());
        return vo;
    }
}