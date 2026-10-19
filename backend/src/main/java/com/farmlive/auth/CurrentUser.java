package com.farmlive.auth;

public record CurrentUser(
        Long id,
        String username,
        String displayName,
        String roleCode,
        String roleName,
        Long farmerId,
        String farmerName,
        boolean mustChangePassword
) {
}
