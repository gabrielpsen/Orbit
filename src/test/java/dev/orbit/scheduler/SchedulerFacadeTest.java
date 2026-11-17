package dev.orbit.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import dev.orbit.domain.JobEntity;
import dev.orbit.repository.JobRepository;
import dev.orbit.repository.TenantQuotaRepository;
import dev.orbit.queue.RedisJobQueue;
import dev.orbit.scheduler.SchedulerFacade.CompletionResult;
import dev.orbit.scheduler.WorkerRegistryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class SchedulerFacadeTest {
    @Test
    void rejectsLateCompletionAfterLeaseExpiry() {
        JobRepository jobs = mock(JobRepository.class);
        RedisJobQueue queue = mock(RedisJobQueue.class);
        TenantQuotaRepository quotas = mock(TenantQuotaRepository.class);
        JobClaimService claims = mock(JobClaimService.class);
        WorkerRegistryService workers = mock(WorkerRegistryService.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        SchedulerProperties properties = new SchedulerProperties();
        JobRetryPolicy retryPolicy = new JobRetryPolicy();
        SchedulerFacade facade = new SchedulerFacade(queue, jobs, quotas, claims, retryPolicy, workers,
                properties, new SimpleMeterRegistry(), events);

        JobEntity job = JobEntity.create("tenant-a", "echo", "{}", 5, Instant.now(), 3, null);
        job.markRunning("worker-a", Instant.now().minusSeconds(1));
        UUID token = job.getLeaseToken();
        when(jobs.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));

        CompletionResult result = facade.complete("worker-a", job.getId(), token, true, "{}", "");

        assertThat(result.accepted()).isFalse();
        assertThat(job.getStatus().name()).isEqualTo("RUNNING");
        verify(events, never()).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }
}
