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

    public AuthService(AdminRepository repository, PasswordService passwordService, TokenStore tokenStore) {
        this.repository = repository;
        this.passwordService = passwordService;
        this.tokenStore = tokenStore;
    }

    public Dtos.LoginResponse login(Dtos.LoginRequest request) {
        AdminRepository.AuthAccount account = repository.findAuthByUsername(request.username().trim())
                .orElseThrow(() -> BusinessException.unauthorized("用户名或密码错误"));
        if (!"active".equals(account.status())) {
            throw BusinessException.unauthorized("账号已停用");
        }
        if (!passwordService.matches(request.password(), account.passwordHash())) {
            throw BusinessException.unauthorized("用户名或密码错误");
        }
        if (request.roleCode() != null && !request.roleCode().isBlank()
                && !request.roleCode().trim().equals(account.roleCode())) {
            throw BusinessException.forbidden("请选择正确的角色");
        }
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

    public void changePassword(CurrentUser user, Dtos.ChangePasswordRequest request, String token) {
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
        tokenStore.remove(token);
    }

    private Dtos.UserView toView(CurrentUser user) {
        return new Dtos.UserView(user.id(), user.username(), user.displayName(),
                user.roleCode(), user.roleName(), user.farmerId(), user.farmerName(), user.mustChangePassword());
    }
}
