package com.trip.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 登录用户主体。authority 必须带 ROLE_ 前缀，否则 hasRole("ADMIN") 永远不通过。
 */
@Getter
@AllArgsConstructor
public class LoginUser implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final String role;
    private final Integer status;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        // 禁用状态的拦截在 UserDetailsServiceImpl（抛 1002），这里恒为 true，避免 DisabledException 覆盖业务码
        return true;
    }
}