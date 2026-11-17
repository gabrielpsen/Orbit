package dev.orbit.api;

import dev.orbit.domain.WorkerEntity;
import dev.orbit.domain.WorkerStatus;
import java.time.Instant;

public record WorkerResponse(String workerId, String hostname, WorkerStatus status, int maxConcurrency,
                             int activeJobs, Instant registeredAt, Instant lastHeartbeat, long completedJobs) {
    public static WorkerResponse from(WorkerEntity worker) {
        return new WorkerResponse(worker.getWorkerId(), worker.getHostname(), worker.getStatus(),
                worker.getMaxConcurrency(), worker.getActiveJobs(), worker.getRegisteredAt(),
                worker.getLastHeartbeat(), worker.getCompletedJobs());
    }
}
