package com.farmlive.auth;

import com.farmlive.common.BusinessException;
import com.farmlive.dto.Dtos;
import com.farmlive.repo.AdminRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AdminRepository repository;
    private final PasswordService passwordService;
    private final TokenStore tokenStore;
    private final CaptchaService captchaService;
    private final LoginAttemptService loginAttemptService;
    private final VerifyTokenService verifyTokenService;
    private final com.farmlive.service.AuditService auditService;

    public AuthService(AdminRepository repository, PasswordService passwordService,
                       TokenStore tokenStore, CaptchaService captchaService,
                       LoginAttemptService loginAttemptService, VerifyTokenService verifyTokenService,
                       com.farmlive.service.AuditService auditService) {
        this.repository = repository;
        this.passwordService = passwordService;
        this.tokenStore = tokenStore;
        this.captchaService = captchaService;
        this.loginAttemptService = loginAttemptService;
        this.verifyTokenService = verifyTokenService;
        this.auditService = auditService;
    }

    public Dtos.LoginResponse login(Dtos.LoginRequest request) {
        String username = request.username().trim();
        loginAttemptService.check(username);
        captchaService.verify(request.captchaId(), request.captchaAnswer());
        AdminRepository.AuthAccount account = repository.findAuthByUsername(username)
                .orElseThrow(() -> {
                    loginAttemptService.fail(username);
                    return BusinessException.unauthorized("用户名或密码错误");
                });
        if (!"active".equals(account.status())) {
            throw BusinessException.unauthorized("账号已停用");
        }
        if (!passwordService.matches(request.password(), account.passwordHash())) {
            loginAttemptService.fail(username);
            throw BusinessException.unauthorized("用户名或密码错误");
        }
        if (request.roleCode() != null && !request.roleCode().isBlank()
                && !request.roleCode().trim().equals(account.roleCode())) {
            throw BusinessException.forbidden("角色不匹配");
        }
        loginAttemptService.success(username);
        CurrentUser user = new CurrentUser(
                account.id(), account.username(), account.displayName(),
                account.roleCode(), account.roleName(), account.farmerId(), account.farmerName(),
                account.mustChangePassword()
        );
        String token = tokenStore.create(user);
        repository.updateLastLogin(account.id());
        return new Dtos.LoginResponse(token, toView(user));
    }

    public Dtos.UserView me(CurrentUser user) {
        Dtos.UserView latest = repository.findUserViewById(user.id())
                .orElseThrow(() -> BusinessException.unauthorized("账号不存在"));
        return latest;
    }

    /**
     * 敏感操作二次验证：复核当前账号密码，签发 5 分钟有效的验证令牌并留审计。
     */
    public Dtos.VerifyTokenView verifyPassword(CurrentUser user, Dtos.VerifyPasswordRequest request) {
        AdminRepository.AuthAccount account = repository.findAuthByUsername(user.username())
                .orElseThrow(() -> BusinessException.unauthorized("账号不存在"));
        if (!passwordService.matches(request.password(), account.passwordHash())) {
            // 用 400 而非 401：前端把 401 统一当作登录态失效处理，密码输错不应清除登录态
            throw BusinessException.badRequest("PASSWORD_WRONG", "密码不正确");
        }
        String token = verifyTokenService.issue(user.id());
        auditService.record(user, "PASSWORD_VERIFY", "敏感操作二次验证通过（" + user.username() + "）");
        return new Dtos.VerifyTokenView(token, 300);
    }

    public Dtos.LoginResponse changePassword(CurrentUser user, Dtos.ChangePasswordRequest request, String token) {
        AdminRepository.AuthAccount account = repository.findAuthByUsername(user.username())
                .orElseThrow(() -> BusinessException.unauthorized("账号不存在"));
        if (!passwordService.matches(request.oldPassword(), account.passwordHash())) {
            throw BusinessException.badRequest("旧密码不正确");
        }
        try {
            passwordService.validateStrength(request.newPassword());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest(ex.getMessage());
        }
        repository.updatePassword(user.id(), passwordService.encode(request.newPassword()), false);
        // 旧 token 失效，签发新 token；返回 mustChangePassword=false 的最新用户态，前端免再登录
        tokenStore.remove(token);
        CurrentUser refreshed = new CurrentUser(user.id(), user.username(), user.displayName(),
                user.roleCode(), user.roleName(), user.farmerId(), user.farmerName(), false);
        String newToken = tokenStore.create(refreshed);
        Dtos.UserView view = toView(refreshed);
        auditService.record(user, "PASSWORD_CHANGE", "修改密码（首登或自助）");
        return new Dtos.LoginResponse(newToken, view);
    }

    private Dtos.UserView toView(CurrentUser user) {
        return new Dtos.UserView(user.id(), user.username(), user.displayName(),
                user.roleCode(), user.roleName(), user.farmerId(), user.farmerName(), user.mustChangePassword());
    }
}
