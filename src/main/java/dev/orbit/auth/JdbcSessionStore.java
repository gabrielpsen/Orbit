package dev.orbit.auth;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class JdbcSessionStore implements SessionStore {
    private final JdbcClient jdbc;

    public JdbcSessionStore(DataSource dataSource) { this.jdbc = JdbcClient.create(dataSource); }

    @Override
    public void create(String tokenHash, UUID userId, Instant createdAt, Instant expiresAt) {
        jdbc.sql("INSERT INTO user_sessions (token_hash, user_id, created_at, expires_at) VALUES (:hash, :user, :created, :expires)")
                .param("hash", tokenHash).param("user", userId)
                .param("created", createdAt.atOffset(ZoneOffset.UTC)).param("expires", expiresAt.atOffset(ZoneOffset.UTC))
                .update();
    }

    @Override
    public Optional<SessionPrincipal> findActive(String tokenHash, Instant now) {
        return jdbc.sql("""
                SELECT u.id, u.username, u.role, s.expires_at
                  FROM user_sessions s JOIN users u ON u.id = s.user_id
                 WHERE s.token_hash = :hash AND s.expires_at > :now AND u.enabled
                """)
                .param("hash", tokenHash).param("now", now.atOffset(ZoneOffset.UTC))
                .query((rs, row) -> new SessionPrincipal(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        Role.valueOf(rs.getString("role")),
                        rs.getObject("expires_at", OffsetDateTime.class).toInstant()))
                .optional();
    }

    @Override
    public void delete(String tokenHash) {
        jdbc.sql("DELETE FROM user_sessions WHERE token_hash = :hash").param("hash", tokenHash).update();
    }

    @Override
    public void deleteForUser(UUID userId) {
        jdbc.sql("DELETE FROM user_sessions WHERE user_id = :user").param("user", userId).update();
    }

    @Override
    public int deleteExpired(Instant now) {
        return jdbc.sql("DELETE FROM user_sessions WHERE expires_at <= :now").param("now", now.atOffset(ZoneOffset.UTC)).update();
    }
}
