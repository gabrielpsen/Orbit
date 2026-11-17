package dev.orbit.api;

import dev.orbit.domain.JobEntity;
import dev.orbit.domain.JobStatus;
import java.time.Instant;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String tenantId,
        String taskType,
        String payloadJson,
        int priority,
        JobStatus status,
        Instant scheduledAt,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        int attempts,
        int maxAttempts,
        String workerId,
        String lastError,
        String outputJson
) {
    public static JobResponse from(JobEntity job) {
        return new JobResponse(job.getId(), job.getTenantId(), job.getTaskType(), job.getPayloadJson(),
                job.getPriority(), job.getStatus(), job.getScheduledAt(), job.getCreatedAt(),
                job.getStartedAt(), job.getFinishedAt(), job.getAttempts(), job.getMaxAttempts(),
                job.getWorkerId(), job.getLastError(), job.getOutputJson());
    }
}
