package dev.orbit.api;

import dev.orbit.security.ApiTokenFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/config")
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class ConfigController {

    private final String environment;
    private final String role;
    private final String grpcHost;
    private final int grpcPort;
    private final long dispatchIntervalMs;
    private final long leaseSeconds;
    private final long heartbeatTimeoutSeconds;
    private final long recoveryIntervalMs;
    private final int defaultTenantConcurrency;
    private final int workerConcurrency;
    private final long workerPollIntervalMs;
    private final long workerHeartbeatIntervalMs;
    private final long workerMaxRuntimeSeconds;
    private final String prometheusUrl;
    private final String grafanaUrl;

    public ConfigController(
            @Value("${orbit.security.environment:development}") String environment,
            @Value("${orbit.role:scheduler}") String role,
            @Value("${orbit.grpc.host:localhost}") String grpcHost,
            @Value("${orbit.grpc.port:9090}") int grpcPort,
            @Value("${orbit.scheduler.dispatch-interval-ms:500}") long dispatchIntervalMs,
            @Value("${orbit.scheduler.lease-seconds:30}") long leaseSeconds,
            @Value("${orbit.scheduler.heartbeat-timeout-seconds:20}") long heartbeatTimeoutSeconds,
            @Value("${orbit.scheduler.recovery-interval-ms:5000}") long recoveryIntervalMs,
            @Value("${orbit.scheduler.default-tenant-concurrency:10}") int defaultTenantConcurrency,
            @Value("${orbit.worker.concurrency:8}") int workerConcurrency,
            @Value("${orbit.worker.poll-interval-ms:350}") long workerPollIntervalMs,
            @Value("${orbit.worker.heartbeat-interval-ms:5000}") long workerHeartbeatIntervalMs,
            @Value("${orbit.worker.max-runtime-seconds:900}") long workerMaxRuntimeSeconds,
            @Value("${orbit.monitoring.prometheus-url:http://localhost:9091}") String prometheusUrl,
            @Value("${orbit.monitoring.grafana-url:http://localhost:3000}") String grafanaUrl) {
        this.environment = environment;
        this.role = role;
        this.grpcHost = grpcHost;
        this.grpcPort = grpcPort;
        this.dispatchIntervalMs = dispatchIntervalMs;
        this.leaseSeconds = leaseSeconds;
        this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
        this.recoveryIntervalMs = recoveryIntervalMs;
        this.defaultTenantConcurrency = defaultTenantConcurrency;
        this.workerConcurrency = workerConcurrency;
        this.workerPollIntervalMs = workerPollIntervalMs;
        this.workerHeartbeatIntervalMs = workerHeartbeatIntervalMs;
        this.workerMaxRuntimeSeconds = workerMaxRuntimeSeconds;
        this.prometheusUrl = prometheusUrl;
        this.grafanaUrl = grafanaUrl;
    }

    @GetMapping
    public Map<String, Object> config(HttpServletRequest request) {
        if (!Boolean.TRUE.equals(request.getAttribute(ApiTokenFilter.ADMIN_ATTRIBUTE))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin token required");
        }

        Map<String, Object> scheduler = new LinkedHashMap<>();
        scheduler.put("dispatchIntervalMs", dispatchIntervalMs);
        scheduler.put("leaseSeconds", leaseSeconds);
        scheduler.put("heartbeatTimeoutSeconds", heartbeatTimeoutSeconds);
        scheduler.put("recoveryIntervalMs", recoveryIntervalMs);
        scheduler.put("defaultTenantConcurrency", defaultTenantConcurrency);

        Map<String, Object> worker = new LinkedHashMap<>();
        worker.put("concurrency", workerConcurrency);
        worker.put("pollIntervalMs", workerPollIntervalMs);
        worker.put("heartbeatIntervalMs", workerHeartbeatIntervalMs);
        worker.put("maxRuntimeSeconds", workerMaxRuntimeSeconds);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("environment", environment);
        result.put("role", role);
        result.put("tlsEnabled", "production".equalsIgnoreCase(environment));
        Map<String, Object> grpc = new LinkedHashMap<>();
        grpc.put("host", grpcHost);
        grpc.put("port", grpcPort);
        result.put("grpc", grpc);
        result.put("scheduler", scheduler);
        result.put("worker", worker);
        result.put("taskTypes", List.of("echo", "sleep", "sum"));
        result.put("endpoints", Map.of("prometheus", prometheusUrl, "grafana", grafanaUrl));
        return result;
    }
}
