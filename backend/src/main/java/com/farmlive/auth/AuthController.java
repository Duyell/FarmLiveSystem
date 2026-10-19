package com.farmlive.auth;

import com.farmlive.common.ApiResponse;
import com.farmlive.dto.Dtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final TokenStore tokenStore;

    public AuthController(AuthService authService, TokenStore tokenStore) {
        this.authService = authService;
        this.tokenStore = tokenStore;
    }

    @PostMapping("/login")
    public ApiResponse<Dtos.LoginResponse> login(@Valid @RequestBody Dtos.LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<Dtos.UserView> me(@RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user) {
        return ApiResponse.ok(authService.me(user));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @RequestAttribute(AuthInterceptor.CURRENT_TOKEN) String token,
            @Valid @RequestBody Dtos.ChangePasswordRequest request) {
        authService.changePassword(user, request, token);
        return ApiResponse.ok();
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestAttribute(AuthInterceptor.CURRENT_TOKEN) String token) {
        tokenStore.remove(token);
        return ApiResponse.ok();
    }
}
