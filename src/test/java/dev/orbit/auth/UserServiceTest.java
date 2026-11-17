package dev.orbit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class UserServiceTest {
    private static final String PASSWORD = "a-long-enough-password";

    private final InMemoryStores.MutableClock clock = new InMemoryStores.MutableClock();
    private final InMemoryStores.Users users = new InMemoryStores.Users(clock);
    private final InMemoryStores.Sessions sessions = new InMemoryStores.Sessions(users);
    private final PasswordHasher hasher = new PasswordHasher(1_000);
    private final UserService service = new UserService(users, sessions, hasher);
    private final AuthService auth = new AuthService(users, sessions, hasher, Duration.ofHours(8), clock);

    @Test
    void createsUsersWithAHashedPassword() {
        UserAccount created = service.create("bob", PASSWORD, Role.VIEWER);
        assertThat(created.role()).isEqualTo(Role.VIEWER);
        assertThat(created.passwordHash()).doesNotContain(PASSWORD);
        assertThat(hasher.verify(PASSWORD, created.passwordHash())).isTrue();
    }

    @Test
    void rejectsShortPasswordsAndBadUsernames() {
        assertStatus(() -> service.create("bob", "short", Role.VIEWER), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create("b", PASSWORD, Role.VIEWER), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create("has space", PASSWORD, Role.VIEWER), HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsDuplicateUsernamesIgnoringCase() {
        service.create("bob", PASSWORD, Role.VIEWER);
        assertStatus(() -> service.create("BOB", PASSWORD, Role.VIEWER), HttpStatus.CONFLICT);
    }

    @Test
    void defaultsToTheLeastPrivilegedRole() {
        assertThat(service.create("bob", PASSWORD, null).role()).isEqualTo(Role.VIEWER);
    }

    @Test
    void anAdministratorCannotDisableOrDemoteThemselves() {
        service.create("alice", PASSWORD, Role.ADMIN);
        UserAccount other = service.create("carol", PASSWORD, Role.ADMIN);
        UserAccount alice = users.findByUsername("alice").orElseThrow();

        assertStatus(() -> service.update(alice.id(), null, false, null, "alice"), HttpStatus.CONFLICT);
        assertStatus(() -> service.update(alice.id(), Role.VIEWER, null, null, "ALICE"), HttpStatus.CONFLICT);
        assertThat(service.update(other.id(), Role.VIEWER, null, null, "alice").role()).isEqualTo(Role.VIEWER);
    }

    @Test
    void theLastEnabledAdministratorCannotBeRemoved() {
        UserAccount only = service.create("alice", PASSWORD, Role.ADMIN);
        assertStatus(() -> service.update(only.id(), null, false, null, "api-token"), HttpStatus.CONFLICT);
        assertStatus(() -> service.update(only.id(), Role.VIEWER, null, null, "api-token"), HttpStatus.CONFLICT);
    }

    @Test
    void disablingOrResettingAPasswordEndsActiveSessions() {
        service.create("alice", PASSWORD, Role.ADMIN);
        UserAccount bob = service.create("bob", PASSWORD, Role.VIEWER);

        String first = auth.login("bob", PASSWORD).token();
        service.update(bob.id(), null, null, "another-long-password", "alice");
        assertThat(auth.authenticate(first)).isEmpty();
        assertThat(auth.login("bob", "another-long-password").token()).isNotBlank();

        String second = auth.login("bob", "another-long-password").token();
        service.update(bob.id(), null, false, null, "alice");
        assertThat(auth.authenticate(second)).isEmpty();
    }

    @Test
    void updatingAnUnknownUserIsNotFound() {
        assertStatus(() -> service.update(UUID.randomUUID(), null, true, null, "alice"), HttpStatus.NOT_FOUND);
    }

    private static void assertStatus(Runnable action, HttpStatus expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(expected));
    }
}
