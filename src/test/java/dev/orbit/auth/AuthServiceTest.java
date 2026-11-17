package dev.orbit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
    private static final String PASSWORD = "a-long-enough-password";

    private final InMemoryStores.MutableClock clock = new InMemoryStores.MutableClock();
    private final InMemoryStores.Users users = new InMemoryStores.Users(clock);
    private final InMemoryStores.Sessions sessions = new InMemoryStores.Sessions(users);
    private final PasswordHasher hasher = new PasswordHasher(1_000);
    private final AuthService auth = new AuthService(users, sessions, hasher, Duration.ofHours(8), clock);

    @BeforeEach
    void seed() {
        users.create("alice", hasher.hash(PASSWORD), Role.ADMIN);
    }

    @Test
    void loginIssuesATokenThatAuthenticates() {
        AuthService.LoginResult result = auth.login("alice", PASSWORD);

        assertThat(result.token()).hasSizeGreaterThanOrEqualTo(40);
        assertThat(result.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofHours(8)));
        assertThat(auth.authenticate(result.token())).hasValueSatisfying(p -> {
            assertThat(p.username()).isEqualTo("alice");
            assertThat(p.role()).isEqualTo(Role.ADMIN);
        });
    }

    @Test
    void theRawTokenIsNeverStored() {
        String token = auth.login("alice", PASSWORD).token();
        assertThat(sessions.rows).doesNotContainKey(token);
        assertThat(sessions.rows).containsKey(PasswordHasher.sha256Hex(token));
    }

    @Test
    void usernameLookupIsCaseInsensitive() {
        assertThat(auth.login("ALICE", PASSWORD).user().username()).isEqualTo("alice");
    }

    @Test
    void unknownUserAndWrongPasswordAreIndistinguishable() {
        Throwable unknown = catchFailure(() -> auth.login("nobody", PASSWORD));
        Throwable wrong = catchFailure(() -> auth.login("alice", "wrong-password-value"));
        assertThat(unknown).hasMessage(wrong.getMessage());
    }

    @Test
    void blankCredentialsAreRejected() {
        assertThatThrownBy(() -> auth.login("", "")).isInstanceOf(AuthenticationFailedException.class);
        assertThatThrownBy(() -> auth.login(null, null)).isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void lockoutAfterRepeatedFailuresBlocksEvenTheCorrectPassword() {
        for (int i = 0; i < AuthService.MAX_FAILED_ATTEMPTS; i++) {
            assertThatThrownBy(() -> auth.login("alice", "nope-nope-nope")).isInstanceOf(AuthenticationFailedException.class);
        }
        Throwable locked = catchFailure(() -> auth.login("alice", PASSWORD));
        Throwable unknown = catchFailure(() -> auth.login("nobody", PASSWORD));
        assertThat(locked).isInstanceOf(AuthenticationFailedException.class).hasMessage(unknown.getMessage());
    }

    @Test
    void accountUnlocksAfterTheLockDuration() {
        for (int i = 0; i < AuthService.MAX_FAILED_ATTEMPTS; i++) catchFailure(() -> auth.login("alice", "nope-nope-nope"));
        clock.advance(AuthService.LOCK_DURATION.plusSeconds(1));
        assertThat(auth.login("alice", PASSWORD).token()).isNotBlank();
    }

    @Test
    void successfulLoginResetsTheFailureCounter() {
        for (int i = 0; i < AuthService.MAX_FAILED_ATTEMPTS - 1; i++) catchFailure(() -> auth.login("alice", "nope-nope-nope"));
        auth.login("alice", PASSWORD);
        for (int i = 0; i < AuthService.MAX_FAILED_ATTEMPTS - 1; i++) catchFailure(() -> auth.login("alice", "nope-nope-nope"));
        assertThat(auth.login("alice", PASSWORD).token()).isNotBlank();
    }

    @Test
    void disabledAccountsCannotSignInEvenWithTheRightPassword() {
        UserAccount alice = users.findByUsername("alice").orElseThrow();
        users.update(alice.id(), alice.role(), false, null);
        assertThat(catchFailure(() -> auth.login("alice", PASSWORD))).isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void expiredSessionsStopAuthenticating() {
        String token = auth.login("alice", PASSWORD).token();
        clock.advance(Duration.ofHours(8).plusSeconds(1));
        assertThat(auth.authenticate(token)).isEmpty();
    }

    @Test
    void disablingAUserInvalidatesTheirExistingSession() {
        String token = auth.login("alice", PASSWORD).token();
        UserAccount alice = users.findByUsername("alice").orElseThrow();
        users.update(alice.id(), alice.role(), false, null);
        assertThat(auth.authenticate(token)).isEmpty();
    }

    @Test
    void logoutRevokesTheToken() {
        String token = auth.login("alice", PASSWORD).token();
        auth.logout(token);
        assertThat(auth.authenticate(token)).isEmpty();
    }

    @Test
    void unknownOrBlankTokensDoNotAuthenticate() {
        assertThat(auth.authenticate("not-a-session")).isEmpty();
        assertThat(auth.authenticate("")).isEmpty();
        assertThat(auth.authenticate(null)).isEmpty();
    }

    private static Throwable catchFailure(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            return failure;
        }
        throw new AssertionError("Expected the action to fail");
    }
}
