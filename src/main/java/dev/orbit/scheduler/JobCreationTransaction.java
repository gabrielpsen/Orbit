package dev.orbit.scheduler;

import dev.orbit.api.CreateJobRequest;
import dev.orbit.api.JobResponse;
import dev.orbit.domain.JobEntity;
import dev.orbit.repository.JobRepository;
import dev.orbit.repository.TenantQuotaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class JobCreationTransaction {
    private final JobRepository jobs;
    private final TenantQuotaRepository quotas;
    private final MeterRegistry metrics;
    private final int defaultTenantConcurrency;

    public JobCreationTransaction(JobRepository jobs, TenantQuotaRepository quotas, MeterRegistry metrics,
                                  @Value("${orbit.scheduler.default-tenant-concurrency:10}") int defaultTenantConcurrency) {
        this.jobs = jobs;
        this.quotas = quotas;
        this.metrics = metrics;
        this.defaultTenantConcurrency = defaultTenantConcurrency;
    }

    @Transactional
    public JobResponse create(CreateJobRequest request, String payloadJson) {
        quotas.createDefaultIfAbsent(request.tenantId(), defaultTenantConcurrency);
        Instant scheduledAt = request.scheduledAt() == null ? Instant.now() : request.scheduledAt();
        int maxAttempts = request.maxAttempts() == null ? 5 : request.maxAttempts();
        JobEntity job = JobEntity.create(request.tenantId(), request.taskType(), payloadJson,
                request.priority(), scheduledAt, maxAttempts,
                request.idempotencyKey() == null || request.idempotencyKey().isBlank() ? null : request.idempotencyKey());
        JobEntity saved = jobs.saveAndFlush(job);
        metrics.counter("orbit.jobs.submitted").increment();
        return JobResponse.from(saved);
    }
}
