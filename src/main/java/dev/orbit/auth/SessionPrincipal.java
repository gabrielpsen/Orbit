package dev.orbit.auth;

import java.time.Instant;
import java.util.UUID;

public record SessionPrincipal(UUID userId, String username, Role role, Instant expiresAt) { }
