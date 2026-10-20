package com.farmlive.auth;

import com.farmlive.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败锁定：同一用户名连续失败 5 次，锁定 15 分钟。
 * 单实例内存实现，多实例部署时需换成共享存储。
 */
@Service
public class LoginAttemptService {
    private static final int MAX_FAILURES = 5;
    private static final int LOCK_MINUTES = 15;

    private record Attempt(int failures, LocalDateTime lockedUntil) {
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public void check(String username) {
        Attempt attempt = attempts.get(key(username));
        if (attempt == null || attempt.lockedUntil() == null) return;
        if (attempt.lockedUntil().isAfter(LocalDateTime.now())) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "ACCOUNT_LOCKED",
                    "登录失败次数过多，请 15 分钟后再试");
        }
        attempts.remove(key(username));
    }

    public void fail(String username) {
        attempts.compute(key(username), (k, old) -> {
            int failures = old == null ? 1 : old.failures() + 1;
            LocalDateTime lockedUntil = failures >= MAX_FAILURES
                    ? LocalDateTime.now().plusMinutes(LOCK_MINUTES) : null;
            return new Attempt(failures, lockedUntil);
        });
    }

    public void success(String username) {
        attempts.remove(key(username));
    }

    private String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
