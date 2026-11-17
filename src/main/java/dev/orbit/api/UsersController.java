package dev.orbit.api;

import dev.orbit.auth.Role;
import dev.orbit.auth.UserAccount;
import dev.orbit.auth.UserService;
import dev.orbit.security.ApiTokenFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/users")
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class UsersController {
    public record UserResponse(UUID id, String username, Role role, boolean enabled, boolean locked,
                               Instant createdAt, Instant lastLoginAt) {
        static UserResponse from(UserAccount user) {
            return new UserResponse(user.id(), user.username(), user.role(), user.enabled(),
                    user.isLocked(Clock.systemUTC().instant()), user.createdAt(), user.lastLoginAt());
        }
    }

    public record CreateUserRequest(@NotBlank @Size(max = 64) String username,
                                    @NotBlank @Size(max = 128) String password, Role role) { }

    public record UpdateUserRequest(Role role, Boolean enabled, @Size(max = 128) String password) { }

    private final UserService users;

    public UsersController(UserService users) { this.users = users; }

    @GetMapping
    public List<UserResponse> list(HttpServletRequest request) {
        requireAdministrator(request);
        return users.list().stream().map(UserResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest body, HttpServletRequest request) {
        requireAdministrator(request);
        return UserResponse.from(users.create(body.username(), body.password(), body.role()));
    }

    @PatchMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest body, HttpServletRequest request) {
        requireAdministrator(request);
        String actor = (String) request.getAttribute(ApiTokenFilter.USERNAME_ATTRIBUTE);
        return UserResponse.from(users.update(id, body.role(), body.enabled(), body.password(), actor));
    }

    private static void requireAdministrator(HttpServletRequest request) {
        boolean admin = Boolean.TRUE.equals(request.getAttribute(ApiTokenFilter.ADMIN_ATTRIBUTE));
        boolean readOnly = Boolean.TRUE.equals(request.getAttribute(ApiTokenFilter.READ_ONLY_ATTRIBUTE));
        if (!admin || readOnly) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access required");
    }
}
