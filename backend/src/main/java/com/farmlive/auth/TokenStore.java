package com.farmlive.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenStore {
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final long ttlHours;

    public TokenStore(@Value("${app.token.ttl-hours:8}") long ttlHours) {
        this.ttlHours = ttlHours;
    }

    public String create(CurrentUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new Session(user, Instant.now().plus(ttlHours, ChronoUnit.HOURS)));
        return token;
    }

    public Optional<CurrentUser> get(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        Session session = sessions.get(token);
        if (session == null) return Optional.empty();
        if (session.expiresAt.isBefore(Instant.now())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session.user);
    }

    public void remove(String token) {
        if (token != null) sessions.remove(token);
    }

    private record Session(CurrentUser user, Instant expiresAt) {
    }
}
