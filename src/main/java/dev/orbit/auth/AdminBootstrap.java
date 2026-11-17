package dev.orbit.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserStore users;
    private final PasswordHasher hasher;
    private final String username;
    private final String password;

    public AdminBootstrap(UserStore users, PasswordHasher hasher,
                          @Value("${orbit.auth.admin-username:admin}") String username,
                          @Value("${orbit.auth.admin-password:}") String password) {
        this.users = users;
        this.hasher = hasher;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;
        if (password == null || password.isBlank()) {
            log.warn("No dashboard users exist. Set ORBIT_ADMIN_PASSWORD to create the first administrator; "
                    + "the API token still works for scripts.");
            return;
        }
        if (password.length() < UserService.MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "ORBIT_ADMIN_PASSWORD must be at least " + UserService.MIN_PASSWORD_LENGTH + " characters");
        }
        try {
            users.create(username.trim(), hasher.hash(password), Role.ADMIN);
            log.info("Created initial administrator '{}'", username.trim());
        } catch (UserStore.DuplicateUsernameException raced) {
            log.info("Initial administrator was created by another scheduler instance");
        }
    }
}
