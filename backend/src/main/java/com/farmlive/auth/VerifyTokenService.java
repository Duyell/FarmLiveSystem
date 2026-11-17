package com.farmlive.auth;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 敏感操作二次验证令牌：密码复核通过后签发，5 分钟内有效，一次性用途由调用方保证。
 */
@Service
public class VerifyTokenService {
    private static final long TTL_SECONDS = 300;

    private final ConcurrentHashMap<String, VerifySession> tokens = new ConcurrentHashMap<>();

    public String issue(Long accountId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, new VerifySession(accountId, Instant.now().plusSeconds(TTL_SECONDS)));
        return token;
    }

    /**
     * 校验令牌：属于该账号且未过期。校验后立即失效（一次性）。
     */
    public boolean consume(Long accountId, String token) {
        if (token == null || token.isBlank()) return false;
        VerifySession session = tokens.remove(token);
        return session != null
                && session.accountId().equals(accountId)
                && session.expiresAt().isAfter(Instant.now());
    }

    private record VerifySession(Long accountId, Instant expiresAt) {
    }
}
