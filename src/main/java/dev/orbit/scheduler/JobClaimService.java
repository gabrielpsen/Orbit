package dev.orbit.scheduler;

import dev.orbit.domain.JobEntity;
import dev.orbit.domain.JobStatus;
import dev.orbit.domain.TenantQuotaEntity;
import dev.orbit.domain.WorkerEntity;
import dev.orbit.domain.WorkerStatus;
import dev.orbit.repository.JobRepository;
import dev.orbit.repository.TenantQuotaRepository;
import dev.orbit.repository.WorkerRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class JobClaimService {
    private final JobRepository jobs;
    private final TenantQuotaRepository quotas;
    private final WorkerRepository workers;
    private final SchedulerProperties properties;
    private final JdbcTemplate jdbc;

    public JobClaimService(JobRepository jobs, TenantQuotaRepository quotas, WorkerRepository workers,
                           SchedulerProperties properties, JdbcTemplate jdbc) {
        this.jobs = jobs;
        this.quotas = quotas;
        this.workers = workers;
        this.properties = properties;
        this.jdbc = jdbc;
    }

    @Transactional
    public Optional<JobEntity> claim(UUID id, String workerId, long expectedEpoch) {
        // FOR SHARE blocks the next leader's epoch update.
        Long currentEpoch = jdbc.queryForObject(
                "SELECT epoch FROM scheduler_fence WHERE singleton_id = 1 FOR SHARE", Long.class);
        if (currentEpoch == null || currentEpoch != expectedEpoch) return Optional.empty();

        WorkerEntity worker = workers.findByIdForUpdate(workerId).orElse(null);
        if (worker == null || worker.getStatus() != WorkerStatus.ONLINE) return Optional.empty();
        if (jobs.countByWorkerIdAndStatus(workerId, JobStatus.RUNNING) >= worker.getMaxConcurrency()) {
            return Optional.empty();
        }

        Optional<JobEntity> candidate = jobs.findByIdForUpdate(id);
        if (candidate.isEmpty()) return Optional.empty();
        JobEntity job = candidate.get();
        Instant now = Instant.now();
        if (job.getStatus() != JobStatus.PENDING || job.getScheduledAt().isAfter(now)) return Optional.empty();

        TenantQuotaEntity quota = quotas.findByTenantIdForUpdate(job.getTenantId()).orElse(null);
        int maxConcurrent = quota == null ? properties.getDefaultTenantConcurrency() : quota.getMaxConcurrentJobs();
        if (jobs.countByTenantIdAndStatus(job.getTenantId(), JobStatus.RUNNING) >= maxConcurrent) {
            return Optional.empty();
        }

        job.markRunning(workerId, now.plusSeconds(properties.getLeaseSeconds()));
        return Optional.of(job);
    }
}
