package dev.orbit.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class AuthService {
    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserStore users;
    private final SessionStore sessions;
    private final PasswordHasher hasher;
    private final Duration sessionTtl;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public AuthService(UserStore users, SessionStore sessions, PasswordHasher hasher,
                       @Value("${orbit.auth.session-ttl-minutes:480}") long sessionTtlMinutes) {
        this(users, sessions, hasher, Duration.ofMinutes(sessionTtlMinutes), Clock.systemUTC());
    }

    AuthService(UserStore users, SessionStore sessions, PasswordHasher hasher, Duration sessionTtl, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.hasher = hasher;
        this.sessionTtl = sessionTtl;
        this.clock = clock;
    }

    public record LoginResult(String token, Instant expiresAt, UserAccount user) { }

    // Same message and same cost for every failure, so usernames can't be probed.
    public LoginResult login(String username, String password) {
        String name = username == null ? "" : username.trim();
        String secret = password == null ? "" : password;
        Instant now = clock.instant();

        Optional<UserAccount> found = name.isEmpty() ? Optional.empty() : users.findByUsername(name);
        if (found.isEmpty()) {
            hasher.verifyDummy(secret);
            throw AuthenticationFailedException.invalidCredentials();
        }
        UserAccount user = found.get();
        if (user.isLocked(now)) {
            hasher.verifyDummy(secret);
            throw AuthenticationFailedException.invalidCredentials();
        }
        if (!hasher.verify(secret, user.passwordHash())) {
            users.recordFailedAttempt(user.id(), MAX_FAILED_ATTEMPTS, now.plus(LOCK_DURATION));
            log.warn("Failed login for user '{}'", user.username());
            throw AuthenticationFailedException.invalidCredentials();
        }
        if (!user.enabled()) throw AuthenticationFailedException.invalidCredentials();

        users.recordSuccessfulLogin(user.id(), now);
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant expiresAt = now.plus(sessionTtl);
        sessions.create(PasswordHasher.sha256Hex(token), user.id(), now, expiresAt);
        log.info("User '{}' signed in", user.username());
        return new LoginResult(token, expiresAt, user);
    }

    public Optional<SessionPrincipal> authenticate(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return sessions.findActive(PasswordHasher.sha256Hex(token), clock.instant());
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) sessions.delete(PasswordHasher.sha256Hex(token));
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    void purgeExpiredSessions() {
        int removed = sessions.deleteExpired(clock.instant());
        if (removed > 0) log.debug("Removed {} expired sessions", removed);
    }
}
