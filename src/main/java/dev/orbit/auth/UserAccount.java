package dev.orbit.auth;

import java.time.Instant;
import java.util.UUID;

public record UserAccount(
        UUID id,
        String username,
        String passwordHash,
        Role role,
        boolean enabled,
        int failedAttempts,
        Instant lockedUntil,
        Instant createdAt,
        Instant lastLoginAt) {

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
