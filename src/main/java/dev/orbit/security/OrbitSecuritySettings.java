package dev.orbit.security;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OrbitSecuritySettings {
    public static final String ADMIN_SCOPE = "*";

    private final String environment;
    private final String role;
    private final String apiToken;
    private final String workerToken;
    private final String databasePassword;
    private final String redisPassword;
    private final Map<String, String> tenantTokens;
    private final String grpcCa;
    private final String grpcCertificate;
    private final String grpcPrivateKey;

    public OrbitSecuritySettings(
            @Value("${orbit.security.environment:development}") String environment,
            @Value("${orbit.role:scheduler}") String role,
            @Value("${orbit.security.api-token:local-dev-api-token-change-me}") String apiToken,
            @Value("${orbit.security.worker-token:local-dev-worker-token-change-me}") String workerToken,
            @Value("${spring.datasource.password:}") String databasePassword,
            @Value("${spring.data.redis.password:}") String redisPassword,
            @Value("${orbit.security.tenant-tokens:}") String tenantTokens,
            @Value("${orbit.security.grpc-ca:}") String grpcCa,
            @Value("${orbit.security.grpc-certificate:}") String grpcCertificate,
            @Value("${orbit.security.grpc-private-key:}") String grpcPrivateKey) {
        this.environment = environment;
        this.role = role;
        this.apiToken = apiToken;
        this.workerToken = workerToken;
        this.databasePassword = databasePassword;
        this.redisPassword = redisPassword;
        this.tenantTokens = parseTenantTokens(tenantTokens);
        this.grpcCa = grpcCa;
        this.grpcCertificate = grpcCertificate;
        this.grpcPrivateKey = grpcPrivateKey;
    }

    @PostConstruct
    void validateConfiguration() {
        if (!isDevelopment() && !isProduction()) {
            throw new IllegalStateException("ORBIT_ENV must be either 'development' or 'production'; unknown values never fall back to plaintext mode");
        }
        if (!"scheduler".equalsIgnoreCase(role) && !"worker".equalsIgnoreCase(role)) {
            throw new IllegalStateException("ORBIT_ROLE must be either 'scheduler' or 'worker'");
        }
        if (!"scheduler".equalsIgnoreCase(role) && !tenantTokens.isEmpty()) {
            throw new IllegalStateException("ORBIT_TENANT_TOKENS may only be configured on the scheduler role");
        }
        if (constantTimeEquals(apiToken, workerToken)) {
            throw new IllegalStateException("ORBIT_API_TOKEN and ORBIT_WORKER_TOKEN must be different secrets");
        }
        tenantTokens.forEach((tenant, token) -> {
            if (constantTimeEquals(apiToken, token) || constantTimeEquals(workerToken, token)) {
                throw new IllegalStateException("Tenant tokens must be unique and cannot reuse control-plane secrets");
            }
        });
        if (!isProduction()) return;
        requireStrongSecret("ORBIT_WORKER_TOKEN", workerToken);
        if ("scheduler".equalsIgnoreCase(role)) {
            requireStrongSecret("ORBIT_API_TOKEN", apiToken);
            requireStrongSecret("SPRING_DATASOURCE_PASSWORD", databasePassword);
            requireStrongSecret("SPRING_DATA_REDIS_PASSWORD", redisPassword);
            Set<String> schedulerSecrets = new HashSet<>();
            schedulerSecrets.add(apiToken);
            schedulerSecrets.add(workerToken);
            schedulerSecrets.add(databasePassword);
            schedulerSecrets.add(redisPassword);
            tenantTokens.values().forEach(schedulerSecrets::add);
            if (schedulerSecrets.size() != 4 + tenantTokens.size()) {
                throw new IllegalStateException("Scheduler API, worker, database, Redis and tenant secrets must all be unique");
            }
        }
        tenantTokens.forEach((tenant, token) -> {
            if (!"scheduler".equalsIgnoreCase(role)) throw new IllegalStateException("Tenant API tokens must only be configured on the scheduler role");
            requireStrongSecret("tenant token for " + tenant, token);
        });
        if (grpcCa.isBlank() || grpcCertificate.isBlank() || grpcPrivateKey.isBlank()) {
            throw new IllegalStateException("Production requires mutual TLS files: ORBIT_GRPC_TLS_CA, ORBIT_GRPC_TLS_CERT and ORBIT_GRPC_TLS_KEY");
        }
    }

    private static void requireStrongSecret(String name, String value) {
        if (value == null || value.length() < 32 || value.startsWith("local-dev-")) {
            throw new IllegalStateException(name + " must be set to a unique random value of at least 32 characters in production");
        }
    }

    private static Map<String, String> parseTenantTokens(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyMap();
        Map<String, String> parsed = new LinkedHashMap<>();
        Set<String> seenTokens = new HashSet<>();
        for (String entry : raw.split(";")) {
            int separator = entry.indexOf('=');
            if (separator < 1 || separator == entry.length() - 1) {
                throw new IllegalArgumentException("ORBIT_TENANT_TOKENS entries must use tenantId=token separated by semicolons");
            }
            String tenant = entry.substring(0, separator).trim();
            String token = entry.substring(separator + 1).trim();
            if (!tenant.matches("[A-Za-z0-9._-]{1,120}") || token.isBlank()
                    || parsed.putIfAbsent(tenant, token) != null || !seenTokens.add(token)) {
                throw new IllegalArgumentException("Invalid or duplicate tenant token entry; tenant IDs and secrets must be unique");
            }
        }
        return Collections.unmodifiableMap(parsed);
    }

    public String resolveApiScope(String bearerToken) {
        if (constantTimeEquals(apiToken, bearerToken)) return ADMIN_SCOPE;
        for (Map.Entry<String, String> entry : tenantTokens.entrySet()) {
            if (constantTimeEquals(entry.getValue(), bearerToken)) return entry.getKey();
        }
        return null;
    }

    public boolean isValidWorkerAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return false;
        return constantTimeEquals(workerToken, authorization.substring(7));
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isProduction() { return "production".equalsIgnoreCase(environment); }
    public boolean isDevelopment() { return "development".equalsIgnoreCase(environment); }
    public String getGrpcCa() { return grpcCa; }
    public String getGrpcCertificate() { return grpcCertificate; }
    public String getGrpcPrivateKey() { return grpcPrivateKey; }
}
