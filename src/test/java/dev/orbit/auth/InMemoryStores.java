package dev.orbit.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class InMemoryStores {
    private InMemoryStores() { }

    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T12:00:00Z");

        void advance(java.time.Duration duration) { now = now.plus(duration); }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    static final class Users implements UserStore {
        final Map<UUID, UserAccount> byId = new LinkedHashMap<>();
        private final MutableClock clock;

        Users(MutableClock clock) { this.clock = clock; }

        @Override public Optional<UserAccount> findByUsername(String username) {
            return byId.values().stream().filter(u -> u.username().equalsIgnoreCase(username)).findFirst();
        }
        @Override public Optional<UserAccount> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
        @Override public List<UserAccount> findAll() { return new ArrayList<>(byId.values()); }
        @Override public long count() { return byId.size(); }
        @Override public long countEnabledAdminsForUpdate() {
            return byId.values().stream().filter(u -> u.enabled() && u.role() == Role.ADMIN).count();
        }
        @Override public UserAccount create(String username, String passwordHash, Role role) {
            if (findByUsername(username).isPresent()) throw new DuplicateUsernameException(username);
            UserAccount user = new UserAccount(UUID.randomUUID(), username, passwordHash, role, true, 0, null, clock.instant(), null);
            byId.put(user.id(), user);
            return user;
        }
        @Override public void update(UUID id, Role role, boolean enabled, String passwordHash) {
            UserAccount u = byId.get(id);
            byId.put(id, new UserAccount(id, u.username(), passwordHash == null ? u.passwordHash() : passwordHash, role, enabled,
                    enabled ? u.failedAttempts() : 0, passwordHash == null ? u.lockedUntil() : null, u.createdAt(), u.lastLoginAt()));
        }
        @Override public void recordFailedAttempt(UUID id, int maxAttempts, Instant lockUntil) {
            UserAccount u = byId.get(id);
            boolean lock = u.failedAttempts() + 1 >= maxAttempts;
            byId.put(id, new UserAccount(id, u.username(), u.passwordHash(), u.role(), u.enabled(),
                    lock ? 0 : u.failedAttempts() + 1, lock ? lockUntil : u.lockedUntil(), u.createdAt(), u.lastLoginAt()));
        }
        @Override public void recordSuccessfulLogin(UUID id, Instant at) {
            UserAccount u = byId.get(id);
            byId.put(id, new UserAccount(id, u.username(), u.passwordHash(), u.role(), u.enabled(), 0, null, u.createdAt(), at));
        }
    }

    static final class Sessions implements SessionStore {
        record Row(UUID userId, Instant expiresAt) { }
        final Map<String, Row> rows = new LinkedHashMap<>();
        private final Users users;

        Sessions(Users users) { this.users = users; }

        @Override public void create(String tokenHash, UUID userId, Instant createdAt, Instant expiresAt) {
            rows.put(tokenHash, new Row(userId, expiresAt));
        }
        @Override public Optional<SessionPrincipal> findActive(String tokenHash, Instant now) {
            Row row = rows.get(tokenHash);
            if (row == null || !row.expiresAt().isAfter(now)) return Optional.empty();
            return users.findById(row.userId()).filter(UserAccount::enabled)
                    .map(u -> new SessionPrincipal(u.id(), u.username(), u.role(), row.expiresAt()));
        }
        @Override public void delete(String tokenHash) { rows.remove(tokenHash); }
        @Override public void deleteForUser(UUID userId) { rows.values().removeIf(r -> r.userId().equals(userId)); }
        @Override public int deleteExpired(Instant now) {
            int before = rows.size();
            rows.values().removeIf(r -> !r.expiresAt().isAfter(now));
            return before - rows.size();
        }
    }
}
