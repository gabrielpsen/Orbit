package dev.orbit.auth;

public class AuthenticationFailedException extends RuntimeException {
    private AuthenticationFailedException() {
        super("Invalid username or password.");
    }

    public static AuthenticationFailedException invalidCredentials() {
        return new AuthenticationFailedException();
    }
}
