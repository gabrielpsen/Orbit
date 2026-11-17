package dev.orbit.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserStore {
    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findById(UUID id);

    List<UserAccount> findAll();

    long count();

    long countEnabledAdminsForUpdate();

    UserAccount create(String username, String passwordHash, Role role);

    void update(UUID id, Role role, boolean enabled, String passwordHash);

    void recordFailedAttempt(UUID id, int maxAttempts, Instant lockUntil);

    void recordSuccessfulLogin(UUID id, Instant at);

    final class DuplicateUsernameException extends RuntimeException {
        public DuplicateUsernameException(String username) {
            super("Username already exists: " + username);
        }
    }
}
