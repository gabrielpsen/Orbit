package dev.orbit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.orbit.api.CreateJobRequest;
import dev.orbit.api.JobResponse;
import dev.orbit.scheduler.JobService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"orbit.grpc.port=0", "orbit.scheduler.dispatch-interval-ms=60000",
                "orbit.scheduler.recovery-interval-ms=60000"})
class OrbitIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.7-alpine"))
            .withDatabaseName("orbit").withUsername("orbit").withPassword("orbit");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.10.3-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired JobService jobService;

    @Test
    void submitIsDurableAndIdempotent() {
        CreateJobRequest request = new CreateJobRequest("integration-team", "echo",
                Map.of("message", "hello"), 5, null, 3, "integration-key-001");
        JobResponse first = jobService.submit(request);
        JobResponse second = jobService.submit(request);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(jobService.get(first.id()).status().name()).isEqualTo("PENDING");
    }
}
