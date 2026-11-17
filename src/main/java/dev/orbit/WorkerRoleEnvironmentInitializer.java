package dev.orbit;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public final class WorkerRoleEnvironmentInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final String PROPERTY_SOURCE = "orbit-worker-autoconfiguration-policy";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment environment = context.getEnvironment();
        if (!"worker".equalsIgnoreCase(environment.getProperty("orbit.role", "scheduler"))) return;

        Set<String> exclusions = new LinkedHashSet<>();
        String configured = environment.getProperty("spring.autoconfigure.exclude", "");
        for (String value : configured.split(",")) {
            if (!value.isBlank()) exclusions.add(value.trim());
        }
        exclusions.add(DataSourceAutoConfiguration.class.getName());
        exclusions.add("org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration");
        environment.getPropertySources().addFirst(new MapPropertySource(
                PROPERTY_SOURCE, Map.of("spring.autoconfigure.exclude", String.join(",", exclusions))));
    }
}
