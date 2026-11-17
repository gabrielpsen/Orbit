package dev.orbit.auth;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class UserService {
    public static final int MIN_PASSWORD_LENGTH = 12;
    public static final int MAX_PASSWORD_LENGTH = 128;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,64}");

    private final UserStore users;
    private final SessionStore sessions;
    private final PasswordHasher hasher;

    public UserService(UserStore users, SessionStore sessions, PasswordHasher hasher) {
        this.users = users;
        this.sessions = sessions;
        this.hasher = hasher;
    }

    public List<UserAccount> list() { return users.findAll(); }

    @Transactional
    public UserAccount create(String username, String password, Role role) {
        String name = username == null ? "" : username.trim();
        if (!USERNAME.matcher(name).matches()) {
            throw bad("Username must be 3-64 characters: letters, digits, '.', '_' or '-'.");
        }
        requireValidPassword(password);
        try {
            return users.create(name, hasher.hash(password), role == null ? Role.VIEWER : role);
        } catch (UserStore.DuplicateUsernameException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already taken.");
        }
    }

    @Transactional
    public UserAccount update(UUID id, Role role, Boolean enabled, String newPassword, String actingUsername) {
        UserAccount target = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        Role nextRole = role == null ? target.role() : role;
        boolean nextEnabled = enabled == null ? target.enabled() : enabled;
        boolean demotes = !(nextEnabled && nextRole == Role.ADMIN);

        if (demotes && target.username().equalsIgnoreCase(actingUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot disable or demote your own account.");
        }
        if (demotes && target.enabled() && target.role() == Role.ADMIN && users.countEnabledAdminsForUpdate() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "At least one enabled administrator is required.");
        }
        String hash = null;
        if (newPassword != null) {
            requireValidPassword(newPassword);
            hash = hasher.hash(newPassword);
        }
        users.update(id, nextRole, nextEnabled, hash);
        if (!nextEnabled || hash != null || nextRole != target.role()) sessions.deleteForUser(id);
        return users.findById(id).orElseThrow();
    }

    private static void requireValidPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw bad("Password must be " + MIN_PASSWORD_LENGTH + "-" + MAX_PASSWORD_LENGTH + " characters.");
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
