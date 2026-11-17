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
        if (path.equals("/api/auth/login") || path.equals("/api/auth/captcha")
                || path.equals("/api/health")) return true;

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

        boolean systemAdmin = "system_admin".equals(user.roleCode());
        boolean liveAdmin = "live_admin".equals(user.roleCode());
        boolean readOnly = "GET".equalsIgnoreCase(request.getMethod())
                || "HEAD".equalsIgnoreCase(request.getMethod());

        if (path.startsWith("/api/admin") && !systemAdmin) {
            write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "没有权限");
            return false;
        }
        // 系统管理员：系统配置/角色分配/审计/知识库可用，但农户、场地、设备的数据维护归直播管理员
        if (systemAdmin && !readOnly && (path.startsWith("/api/admin/farmers")
                || path.startsWith("/api/admin/venues") || path.startsWith("/api/admin/devices"))) {
            write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                    "农户资质与资源维护由直播管理员操作");
            return false;
        }

        if (path.startsWith("/api/farmer") && !"farmer".equals(user.roleCode())) {
            write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "该功能仅农户可用");
            return false;
        }

        // 直播管理员负责审核/调度/排队/销售等业务操作；系统管理员仅保留全量只读查看
        if (path.startsWith("/api/live") && !liveAdmin && !(systemAdmin && readOnly)) {
            write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "该功能仅直播管理员可用");
            return false;
        }

        if (path.startsWith("/api/knowledge") && !"system_admin".equals(user.roleCode())) {
            write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "知识库管理仅系统管理员可用");
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
