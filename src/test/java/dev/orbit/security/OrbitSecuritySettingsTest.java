package dev.orbit.security;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class OrbitSecuritySettingsTest {
    @Test
    void resolvesAdminAndTenantTokensWithoutCrossTenantScope() {
        OrbitSecuritySettings settings = new OrbitSecuritySettings("development", "scheduler", "admin-token-value",
                "worker-token-value", "", "", "team-a=tenant-a-secret;team-b=tenant-b-secret", "", "", "");
        assertThat(settings.resolveApiScope("admin-token-value")).isEqualTo("*");
        assertThat(settings.resolveApiScope("tenant-a-secret")).isEqualTo("team-a");
        assertThat(settings.resolveApiScope("tenant-b-secret")).isEqualTo("team-b");
        assertThat(settings.resolveApiScope("wrong-token")).isNull();
    }

    @Test
    void unknownEnvironmentDoesNotSilentlyEnableDevelopmentMode() {
        OrbitSecuritySettings settings = new OrbitSecuritySettings("staging", "scheduler", "admin-token-value",
                "worker-token-value", "", "", "", "", "", "");
        org.assertj.core.api.Assertions.assertThat(settings.isDevelopment()).isFalse();
        org.assertj.core.api.Assertions.assertThat(settings.isProduction()).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(settings::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ORBIT_ENV");
    }

    @Test
    void productionWorkerDoesNotNeedAnApiTokenButMustHaveStrongWorkerSecretAndMtls() {
        OrbitSecuritySettings settings = new OrbitSecuritySettings("production", "worker", "",
                "w".repeat(48), "", "", "", "/run/ca.crt", "/run/worker.crt", "/run/worker.key");
        org.assertj.core.api.Assertions.assertThatCode(settings::validateConfiguration).doesNotThrowAnyException();
    }

    @Test
    void productionWorkerRejectsMissingWorkerSecret() {
        OrbitSecuritySettings settings = new OrbitSecuritySettings("production", "worker", "",
                "local-dev-worker-token-change-me", "", "", "", "/run/ca.crt", "/run/worker.crt", "/run/worker.key");
        org.assertj.core.api.Assertions.assertThatThrownBy(settings::validateConfiguration)
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ORBIT_WORKER_TOKEN");
    }

    @Test
    void rejectsDuplicateTenantSecretsToPreventAmbiguousTenantIdentity() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new OrbitSecuritySettings("development", "scheduler",
                "admin-token-value", "worker-token-value", "", "", "team-a=shared-secret;team-b=shared-secret", "", "", ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("secrets must be unique");
    }

    @Test
    void rejectsAdminAndWorkerTokenReuseEvenInDevelopment() {
        OrbitSecuritySettings settings = new OrbitSecuritySettings("development", "scheduler", "shared-token",
                "shared-token", "", "", "", "", "", "");
        org.assertj.core.api.Assertions.assertThatThrownBy(settings::validateConfiguration)
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("must be different secrets");
    }

    @Test
    void rejectsUnknownRuntimeRoles() {
        OrbitSecuritySettings settings = new OrbitSecuritySettings("development", "both", "admin-token-value",
                "worker-token-value", "", "", "", "", "", "");
        org.assertj.core.api.Assertions.assertThatThrownBy(settings::validateConfiguration)
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ORBIT_ROLE");
    }

    @Test
    void productionSchedulerRequiresDistinctStrongDatabaseAndRedisCredentials() {
        OrbitSecuritySettings valid = new OrbitSecuritySettings("production", "scheduler", "a".repeat(48),
                "b".repeat(48), "c".repeat(48), "d".repeat(48), "", "/run/ca.crt", "/run/server.crt", "/run/server.key");
        org.assertj.core.api.Assertions.assertThatCode(valid::validateConfiguration).doesNotThrowAnyException();

        OrbitSecuritySettings missingRedis = new OrbitSecuritySettings("production", "scheduler", "a".repeat(48),
                "b".repeat(48), "c".repeat(48), "", "", "/run/ca.crt", "/run/server.crt", "/run/server.key");
        org.assertj.core.api.Assertions.assertThatThrownBy(missingRedis::validateConfiguration)
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SPRING_DATA_REDIS_PASSWORD");
    }

    @Test
    void requiresBearerPrefixForWorkerAuthentication() {
        OrbitSecuritySettings settings = new OrbitSecuritySettings("development", "scheduler", "admin-token-value",
                "worker-token-value", "", "", "", "", "", "");
        assertThat(settings.isValidWorkerAuthorization("Bearer worker-token-value")).isTrue();
        assertThat(settings.isValidWorkerAuthorization("worker-token-value")).isFalse();
        assertThat(settings.isValidWorkerAuthorization("Bearer wrong-token")).isFalse();
    }
}
