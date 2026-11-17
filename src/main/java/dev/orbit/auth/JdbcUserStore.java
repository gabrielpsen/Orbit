package dev.orbit.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class JdbcUserStore implements UserStore {
    private static final String COLUMNS =
            "id, username, password_hash, role, enabled, failed_attempts, locked_until, created_at, last_login_at";

    private final JdbcClient jdbc;

    public JdbcUserStore(DataSource dataSource) { this.jdbc = JdbcClient.create(dataSource); }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE lower(username) = lower(:username)")
                .param("username", username).query(JdbcUserStore::map).optional();
    }

    @Override
    public Optional<UserAccount> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE id = :id")
                .param("id", id).query(JdbcUserStore::map).optional();
    }

    @Override
    public List<UserAccount> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM users ORDER BY lower(username)").query(JdbcUserStore::map).list();
    }

    @Override
    public long count() {
        return jdbc.sql("SELECT count(*) FROM users").query(Long.class).single();
    }

    @Override
    public long countEnabledAdminsForUpdate() {
        return jdbc.sql("SELECT id FROM users WHERE role = 'ADMIN' AND enabled FOR UPDATE")
                .query((rs, row) -> rs.getObject("id", UUID.class)).list().size();
    }

    @Override
    public UserAccount create(String username, String passwordHash, Role role) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.sql("INSERT INTO users (id, username, password_hash, role) VALUES (:id, :username, :hash, :role)")
                    .param("id", id).param("username", username).param("hash", passwordHash).param("role", role.name())
                    .update();
        } catch (DuplicateKeyException duplicate) {
            throw new DuplicateUsernameException(username);
        }
        return findById(id).orElseThrow();
    }

    @Override
    public void update(UUID id, Role role, boolean enabled, String passwordHash) {
        jdbc.sql("""
                UPDATE users
                   SET role = :role,
                       enabled = :enabled,
                       password_hash = COALESCE(CAST(:hash AS VARCHAR), password_hash),
                       failed_attempts = CASE WHEN :enabled THEN failed_attempts ELSE 0 END,
                       locked_until = CASE WHEN CAST(:hash AS VARCHAR) IS NOT NULL THEN NULL ELSE locked_until END
                 WHERE id = :id
                """)
                .param("id", id).param("role", role.name()).param("enabled", enabled).param("hash", passwordHash)
                .update();
    }

    @Override
    public void recordFailedAttempt(UUID id, int maxAttempts, Instant lockUntil) {
        jdbc.sql("""
                UPDATE users
                   SET failed_attempts = CASE WHEN failed_attempts + 1 >= :max THEN 0 ELSE failed_attempts + 1 END,
                       locked_until = CASE WHEN failed_attempts + 1 >= :max THEN :lockUntil ELSE locked_until END
                 WHERE id = :id
                """)
                .param("id", id).param("max", maxAttempts).param("lockUntil", utc(lockUntil)).update();
    }

    @Override
    public void recordSuccessfulLogin(UUID id, Instant at) {
        jdbc.sql("UPDATE users SET failed_attempts = 0, locked_until = NULL, last_login_at = :at WHERE id = :id")
                .param("id", id).param("at", utc(at)).update();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static UserAccount map(ResultSet rs, int row) throws SQLException {
        return new UserAccount(
                rs.getObject("id", UUID.class),
                rs.getString("username"),
                rs.getString("password_hash"),
                Role.valueOf(rs.getString("role")),
                rs.getBoolean("enabled"),
                rs.getInt("failed_attempts"),
                instant(rs, "locked_until"),
                instant(rs, "created_at"),
                instant(rs, "last_login_at"));
    }
}
