package dev.orbit.security;

import dev.orbit.auth.AuthService;
import dev.orbit.auth.Role;
import dev.orbit.auth.SessionPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Accepts the API token, tenant tokens and user sessions. Viewers are read-only. */
@Component
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiTokenFilter extends OncePerRequestFilter {
    public static final String TENANT_ATTRIBUTE = "orbit.tenantId";
    public static final String ADMIN_ATTRIBUTE = "orbit.admin";
    public static final String READ_ONLY_ATTRIBUTE = "orbit.readOnly";
    public static final String USERNAME_ATTRIBUTE = "orbit.username";
    public static final String ROLE_ATTRIBUTE = "orbit.userRole";
    public static final String SESSION_EXPIRES_ATTRIBUTE = "orbit.sessionExpiresAt";
    public static final String API_TOKEN_PRINCIPAL = "api-token";

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final OrbitSecuritySettings settings;
    private final AuthService auth;

    public ApiTokenFilter(OrbitSecuritySettings settings, AuthService auth) {
        this.settings = settings;
        this.auth = auth;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.equals("/actuator/health") || path.startsWith("/actuator/health/") || path.equals("/actuator/info")) return true;
        return path.equals(LOGIN_PATH) && "POST".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean actuatorPath = path.startsWith("/actuator/");
        boolean protectedPath = path.startsWith("/api/") || actuatorPath;
        if (!protectedPath) {
            filterChain.doFilter(request, response);
            return;
        }
        String authorization = request.getHeader("Authorization");
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;

        String scope = settings.resolveApiScope(token);
        if (scope != null) {
            boolean admin = OrbitSecuritySettings.ADMIN_SCOPE.equals(scope);
            if (actuatorPath && !admin) {
                reject(response, HttpServletResponse.SC_FORBIDDEN, "forbidden", "Admin token required for actuator endpoints");
                return;
            }
            request.setAttribute(ADMIN_ATTRIBUTE, admin);
            request.setAttribute(USERNAME_ATTRIBUTE, API_TOKEN_PRINCIPAL);
            if (admin) request.setAttribute(ROLE_ATTRIBUTE, Role.ADMIN.name());
            else request.setAttribute(TENANT_ATTRIBUTE, scope);
            filterChain.doFilter(request, response);
            return;
        }

        Optional<SessionPrincipal> session = auth.authenticate(token);
        if (session.isEmpty()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized", "A valid bearer token is required");
            return;
        }
        SessionPrincipal principal = session.get();
        boolean viewer = principal.role() == Role.VIEWER;
        if (viewer && actuatorPath) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "forbidden", "Admin access required for actuator endpoints");
            return;
        }
        if (viewer && !SAFE_METHODS.contains(request.getMethod()) && !path.equals(LOGOUT_PATH)) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "forbidden", "Your account is read-only");
            return;
        }
        request.setAttribute(ADMIN_ATTRIBUTE, true);
        request.setAttribute(READ_ONLY_ATTRIBUTE, viewer);
        request.setAttribute(USERNAME_ATTRIBUTE, principal.username());
        request.setAttribute(ROLE_ATTRIBUTE, principal.role().name());
        request.setAttribute(SESSION_EXPIRES_ATTRIBUTE, principal.expiresAt());
        filterChain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
    }
}
