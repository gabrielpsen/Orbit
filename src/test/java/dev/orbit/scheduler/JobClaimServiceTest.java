package dev.orbit.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.orbit.domain.JobEntity;
import dev.orbit.domain.JobStatus;
import dev.orbit.domain.TenantQuotaEntity;
import dev.orbit.domain.WorkerEntity;
import dev.orbit.repository.JobRepository;
import dev.orbit.repository.TenantQuotaRepository;
import dev.orbit.repository.WorkerRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class JobClaimServiceTest {
    @Mock JobRepository jobs;
    @Mock TenantQuotaRepository quotas;
    @Mock WorkerRepository workers;
    @Mock JdbcTemplate jdbc;
    private JobClaimService service;

    @BeforeEach
    void setUp() {
        SchedulerProperties properties = new SchedulerProperties();
        properties.setLeaseSeconds(30);
        properties.setDefaultTenantConcurrency(10);
        service = new JobClaimService(jobs, quotas, workers, properties, jdbc);
    }

    @Test
    void rejectsAClaimFromAnOldLeaderEpochBeforeTouchingTheWorker() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(12L);
        assertThat(service.claim(UUID.randomUUID(), "worker-a", 11L)).isEmpty();
        verify(workers, never()).findByIdForUpdate("worker-a");
        verify(jobs, never()).findByIdForUpdate(org.mockito.ArgumentMatchers.any(UUID.class));
    }

    @Test
    void enforcesWorkerConcurrencyInsideTheDatabaseClaimTransaction() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(12L);
        when(workers.findByIdForUpdate("worker-a")).thenReturn(Optional.of(new WorkerEntity("worker-a", "host", 1)));
        when(jobs.countByWorkerIdAndStatus("worker-a", JobStatus.RUNNING)).thenReturn(1L);
        assertThat(service.claim(UUID.randomUUID(), "worker-a", 12L)).isEmpty();
        verify(jobs, never()).findByIdForUpdate(org.mockito.ArgumentMatchers.any(UUID.class));
    }

    @Test
    void claimsOnlyWhenLeaderWorkerAndTenantQuotasAllAllowIt() {
        UUID id = UUID.randomUUID();
        JobEntity job = JobEntity.create("tenant-a", "echo", "{\"message\":\"hi\"}", 5,
                Instant.now().minusSeconds(1), 3, null);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(12L);
        when(workers.findByIdForUpdate("worker-a")).thenReturn(Optional.of(new WorkerEntity("worker-a", "host", 2)));
        when(jobs.countByWorkerIdAndStatus("worker-a", JobStatus.RUNNING)).thenReturn(0L);
        when(jobs.findByIdForUpdate(id)).thenReturn(Optional.of(job));
        when(quotas.findByTenantIdForUpdate("tenant-a")).thenReturn(Optional.of(new TenantQuotaEntity("tenant-a", 2, 1)));
        when(jobs.countByTenantIdAndStatus("tenant-a", JobStatus.RUNNING)).thenReturn(0L);

        var claimed = service.claim(id, "worker-a", 12L);
        assertThat(claimed).contains(job);
        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.getLeaseToken()).isNotNull();
        assertThat(job.getLeaseExpiresAt()).isAfter(Instant.now());
    }
}
