package dev.orbit.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SessionStore {
    void create(String tokenHash, UUID userId, Instant createdAt, Instant expiresAt);

    Optional<SessionPrincipal> findActive(String tokenHash, Instant now);

    void delete(String tokenHash);

    void deleteForUser(UUID userId);

    int deleteExpired(Instant now);
}
