package dev.orbit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.orbit.auth.AuthService;
import dev.orbit.auth.Role;
import dev.orbit.auth.SessionPrincipal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiTokenFilterTest {
    private final AuthService auth = mock(AuthService.class);
    private ApiTokenFilter filter;

    @BeforeEach
    void setUp() {
        OrbitSecuritySettings settings = new OrbitSecuritySettings("development", "scheduler", "admin-token-value",
                "worker-token-value", "", "", "tenant-a=tenant-token-value", "", "", "");
        filter = new ApiTokenFilter(settings, auth);
        session("admin-session", Role.ADMIN);
        session("viewer-session", Role.VIEWER);
    }

    private void session(String token, Role role) {
        when(auth.authenticate(token)).thenReturn(Optional.of(
                new SessionPrincipal(UUID.randomUUID(), role.name().toLowerCase(), role, Instant.parse("2030-01-01T00:00:00Z"))));
    }

    private Result call(String method, String path, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (token != null) request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Result(request, response, chain.getRequest() != null);
    }

    record Result(MockHttpServletRequest request, MockHttpServletResponse response, boolean passed) { }

    @Test
    void rejectsRequestsWithoutAValidToken() throws Exception {
        assertThat(call("GET", "/api/v1/stats", null).response().getStatus()).isEqualTo(401);
        assertThat(call("GET", "/api/v1/stats", "garbage").response().getStatus()).isEqualTo(401);
    }

    @Test
    void loginIsPublicButOnlyForPost() throws Exception {
        assertThat(call("POST", "/api/v1/auth/login", null).passed()).isTrue();
        assertThat(call("GET", "/api/v1/auth/login", null).response().getStatus()).isEqualTo(401);
    }

    @Test
    void staticAssetsAreNotProtected() throws Exception {
        assertThat(call("GET", "/assets/index.js", null).passed()).isTrue();
    }

    @Test
    void theAdminApiTokenStillWorksForScripts() throws Exception {
        Result result = call("POST", "/api/v1/jobs", "admin-token-value");
        assertThat(result.passed()).isTrue();
        assertThat(result.request().getAttribute(ApiTokenFilter.ADMIN_ATTRIBUTE)).isEqualTo(true);
        assertThat(result.request().getAttribute(ApiTokenFilter.USERNAME_ATTRIBUTE)).isEqualTo("api-token");
    }

    @Test
    void tenantTokensAreScopedAndNeverAdmin() throws Exception {
        Result result = call("GET", "/api/v1/jobs", "tenant-token-value");
        assertThat(result.request().getAttribute(ApiTokenFilter.ADMIN_ATTRIBUTE)).isEqualTo(false);
        assertThat(result.request().getAttribute(ApiTokenFilter.TENANT_ATTRIBUTE)).isEqualTo("tenant-a");
    }

    @Test
    void adminSessionsCanChangeState() throws Exception {
        Result result = call("POST", "/api/v1/jobs", "admin-session");
        assertThat(result.passed()).isTrue();
        assertThat(result.request().getAttribute(ApiTokenFilter.READ_ONLY_ATTRIBUTE)).isEqualTo(false);
        assertThat(result.request().getAttribute(ApiTokenFilter.ADMIN_ATTRIBUTE)).isEqualTo(true);
    }

    @Test
    void viewerSessionsCanReadButNotWrite() throws Exception {
        Result read = call("GET", "/api/v1/stats", "viewer-session");
        assertThat(read.passed()).isTrue();
        assertThat(read.request().getAttribute(ApiTokenFilter.READ_ONLY_ATTRIBUTE)).isEqualTo(true);

        for (String method : new String[] {"POST", "DELETE", "PATCH", "PUT"}) {
            assertThat(call(method, "/api/v1/jobs", "viewer-session").response().getStatus()).as(method).isEqualTo(403);
        }
    }

    @Test
    void viewersCanStillSignOut() throws Exception {
        assertThat(call("POST", "/api/v1/auth/logout", "viewer-session").passed()).isTrue();
    }

    @Test
    void viewersCannotReachActuatorEndpoints() throws Exception {
        assertThat(call("GET", "/actuator/prometheus", "viewer-session").response().getStatus()).isEqualTo(403);
        assertThat(call("GET", "/actuator/prometheus", "admin-session").passed()).isTrue();
    }
}
