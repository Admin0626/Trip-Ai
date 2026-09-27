package com.trip.security;

import com.trip.common.util.JwtUtil;
import com.trip.common.exception.BizException;
import com.trip.module.user.entity.SysUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：只接受 access token，并实时检查会话与账号状态。
 * ⚠️ 过滤器异常 @RestControllerAdvice 接不到，必须自己往 response 写 401 JSON。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthSessionService sessions;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return List.of("/auth/login", "/auth/register", "/auth/refresh").contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith(JwtUtil.TOKEN_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = auth.substring(JwtUtil.TOKEN_PREFIX.length());
        try {
            SysUser user = sessions.authenticate(token, "access");
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    user.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (BizException e) {
            SecurityContextHolder.clearContext();
            writeError(response, e.getCode());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int code) throws IOException {
        response.setStatus(code == 503 ? 503 : HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        String message = code == 503 ? "认证服务暂不可用，请稍后重试" : "未登录或登录已过期";
        String json = "{\"code\":" + (code == 503 ? 503 : 401) + ",\"message\":\"" + message + "\",\"data\":null,\"timestamp\":" + System.currentTimeMillis() + "}";
        response.getWriter().write(json);
    }
}
