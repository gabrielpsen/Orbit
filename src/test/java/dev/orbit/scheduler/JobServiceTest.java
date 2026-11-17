package dev.orbit.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.orbit.domain.JobEntity;
import dev.orbit.domain.JobStatus;
import dev.orbit.repository.JobRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class JobServiceTest {
    private final JobRepository repository = mock(JobRepository.class);
    private final JobService service = new JobService(repository, new ObjectMapper(), mock(JobCreationTransaction.class));

    @Test
    void filtersByStatusInTheDatabaseWhenNoTenantIsGiven() {
        JobEntity job = JobEntity.create("tenant-a", "echo", "{}", 0, Instant.now(), 3, null);
        when(repository.findTop100ByStatusOrderByCreatedAtDesc(JobStatus.PENDING)).thenReturn(List.of(job));

        assertThat(service.list(null, JobStatus.PENDING)).hasSize(1);
        verify(repository, never()).findTop100ByOrderByCreatedAtDesc();
    }
}
