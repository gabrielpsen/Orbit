package dev.orbit.scheduler;

import dev.orbit.domain.JobEntity;
import dev.orbit.domain.JobStatus;
import dev.orbit.domain.TenantQuotaEntity;
import dev.orbit.queue.RedisJobQueue;
import dev.orbit.repository.JobRepository;
import dev.orbit.repository.TenantQuotaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import java.time.Instant;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class SchedulerFacade {
    private final RedisJobQueue queue;
    private final JobRepository jobs;
    private final TenantQuotaRepository quotas;
    private final JobClaimService claimService;
    private final JobRetryPolicy retryPolicy;
    private final WorkerRegistryService workerRegistry;
    private final SchedulerProperties properties;
    private final MeterRegistry metrics;
    private final ApplicationEventPublisher events;
    private final AtomicInteger dispatchPageCursor = new AtomicInteger();

    public SchedulerFacade(RedisJobQueue queue, JobRepository jobs, TenantQuotaRepository quotas,
                           JobClaimService claimService, JobRetryPolicy retryPolicy,
                           WorkerRegistryService workerRegistry, SchedulerProperties properties,
                           MeterRegistry metrics, ApplicationEventPublisher events) {
        this.queue = queue;
        this.jobs = jobs;
        this.quotas = quotas;
        this.claimService = claimService;
        this.retryPolicy = retryPolicy;
        this.workerRegistry = workerRegistry;
        this.properties = properties;
        this.metrics = metrics;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public void publishDueJobs() {
        final int pageSize = 200;
        final int pagesPerTick = 10;
        Instant now = Instant.now();
        long dueCount = jobs.countByStatusAndScheduledAtLessThanEqual(JobStatus.PENDING, now);
        int totalPages = (int) Math.max(1, Math.ceil(dueCount / (double) pageSize));
        int startPage = Math.floorMod(dispatchPageCursor.getAndAdd(pagesPerTick), totalPages);
        Map<String, Integer> tenantWeights = new HashMap<>();
        for (int offset = 0; offset < Math.min(pagesPerTick, totalPages); offset++) {
            int page = (startPage + offset) % totalPages;
            var batch = jobs.findByStatusAndScheduledAtLessThanEqualOrderByPriorityDescScheduledAtAscCreatedAtAsc(
                    JobStatus.PENDING, now, PageRequest.of(page, pageSize));
            for (JobEntity job : batch) {
                int weight = tenantWeights.computeIfAbsent(job.getTenantId(), tenantId ->
                        quotas.findById(tenantId).map(TenantQuotaEntity::getWeight).orElse(1));
                queue.enqueue(job, weight);
            }
        }
    }

    public Optional<JobEntity> poll(String workerId, long fencingEpoch) {
        for (int attempts = 0; attempts < 25; attempts++) {
            Optional<String> candidateId = queue.popNext();
            if (candidateId.isEmpty()) return Optional.empty();
            try {
                Optional<JobEntity> claimed = claimService.claim(UUID.fromString(candidateId.get()), workerId, fencingEpoch);
                if (claimed.isPresent()) {
                    metrics.counter("orbit.jobs.claimed").increment();
                    return claimed;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Optional.empty();
    }

    @Transactional
    public CompletionResult complete(String workerId, UUID jobId, UUID leaseToken, boolean success, String outputJson, String error) {
        JobEntity job = jobs.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.RUNNING || !workerId.equals(job.getWorkerId())
                || !Objects.equals(job.getLeaseToken(), leaseToken) || job.getLeaseExpiresAt() == null
                || !job.getLeaseExpiresAt().isAfter(Instant.now())) {
            return new CompletionResult(false, job == null ? "NOT_FOUND" : job.getStatus().name());
        }

        if (success) {
            job.markSucceeded(outputJson == null || outputJson.isBlank() ? "{}" : outputJson);
            // Updated after commit to avoid a job/worker lock-order deadlock.
            metrics.counter("orbit.jobs.succeeded").increment();
            events.publishEvent(new WorkerCompletedEvent(workerId));
        } else if (job.getAttempts() >= job.getMaxAttempts()) {
            job.markDead(error);
            metrics.counter("orbit.jobs.dead").increment();
        } else {
            Instant retryAt = Instant.now().plus(retryPolicy.delayForAttempt(job.getAttempts()));
            job.markRetry(error, retryAt);
            metrics.counter("orbit.jobs.retried").increment();
        }
        return new CompletionResult(true, job.getStatus().name());
    }

    @Transactional
    public boolean renewJobLease(String workerId, UUID jobId, UUID leaseToken) {
        JobEntity job = jobs.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.RUNNING
                || !workerId.equals(job.getWorkerId()) || !Objects.equals(job.getLeaseToken(), leaseToken)
                || job.getLeaseExpiresAt() == null || !job.getLeaseExpiresAt().isAfter(Instant.now())) {
            return false;
        }
        job.extendLease(Instant.now().plusSeconds(properties.getLeaseSeconds()));
        return true;
    }

    @Transactional
    public int recoverExpiredLeases() {
        int recovered = 0;
        for (JobEntity job : jobs.findByStatusAndLeaseExpiresAtBefore(JobStatus.RUNNING, Instant.now())) {
            JobEntity locked = jobs.findByIdForUpdate(job.getId()).orElse(null);
            if (locked == null || locked.getStatus() != JobStatus.RUNNING || locked.getLeaseExpiresAt() == null
                    || locked.getLeaseExpiresAt().isAfter(Instant.now())) continue;
            String reason = "Worker lease expired; execution may have been interrupted";
            if (locked.getAttempts() >= locked.getMaxAttempts()) {
                locked.markDead(reason);
                metrics.counter("orbit.jobs.dead", "reason", "lease_expired").increment();
            } else {
                locked.markRetry(reason, Instant.now().plus(retryPolicy.delayForAttempt(locked.getAttempts())));
                metrics.counter("orbit.jobs.recovered").increment();
            }
            recovered++;
        }
        return recovered;
    }

    public int markStaleWorkersOffline() {
        Instant cutoff = Instant.now().minusSeconds(properties.getHeartbeatTimeoutSeconds());
        return workerRegistry.markStaleWorkersOffline(cutoff);
    }

    public record CompletionResult(boolean accepted, String state) { }
}
