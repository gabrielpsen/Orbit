package dev.orbit.api;

import dev.orbit.auth.AuthService;
import dev.orbit.auth.AuthenticationFailedException;
import dev.orbit.security.ApiTokenFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class AuthController {
    public record LoginRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 256) String password) { }

    public record UserSummary(String username, String role) { }

    public record LoginResponse(String token, String tokenType, Instant expiresAt, UserSummary user) { }

    public record SessionInfo(String username, String role, Instant expiresAt) { }

    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthService.LoginResult result = auth.login(request.username(), request.password());
            return ResponseEntity.ok(new LoginResponse(result.token(), "Bearer", result.expiresAt(),
                    new UserSummary(result.user().username(), result.user().role().name())));
        } catch (AuthenticationFailedException failure) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "unauthorized", "message", failure.getMessage()));
        }
    }

    @GetMapping("/me")
    public SessionInfo me(HttpServletRequest request) {
        return new SessionInfo(
                (String) request.getAttribute(ApiTokenFilter.USERNAME_ATTRIBUTE),
                (String) request.getAttribute(ApiTokenFilter.ROLE_ATTRIBUTE),
                (Instant) request.getAttribute(ApiTokenFilter.SESSION_EXPIRES_ATTRIBUTE));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) auth.logout(authorization.substring(7));
    }
}
