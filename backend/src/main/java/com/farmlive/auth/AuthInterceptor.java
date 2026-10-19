package com.farmlive.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmlive.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Optional;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    public static final String CURRENT_USER = "currentUser";
    public static final String CURRENT_TOKEN = "currentToken";

    private final TokenStore tokenStore;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(TokenStore tokenStore, ObjectMapper objectMapper) {
        this.tokenStore = tokenStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        String path = request.getRequestURI();
        if (path.equals("/api/auth/login") || path.equals("/api/health")) return true;

        String token = bearerToken(request);
        Optional<CurrentUser> userOpt = tokenStore.get(token);
        if (userOpt.isEmpty()) {
            write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
            return false;
        }

        CurrentUser user = userOpt.get();
        request.setAttribute(CURRENT_USER, user);
        request.setAttribute(CURRENT_TOKEN, token);

        if (user.mustChangePassword()
                && !path.equals("/api/auth/me")
                && !path.equals("/api/auth/change-password")
                && !path.equals("/api/auth/logout")) {
            write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "请先修改初始密码");
            return false;
        }

        if (path.startsWith("/api/admin") && !"system_admin".equals(user.roleCode())) {
            write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "没有权限");
            return false;
        }

        return true;
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null) return null;
        if (header.startsWith("Bearer ")) return header.substring(7).trim();
        return header.trim();
    }

    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(code, message));
    }
}
