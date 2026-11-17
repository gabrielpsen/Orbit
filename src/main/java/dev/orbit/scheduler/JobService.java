package dev.orbit.scheduler;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import dev.orbit.api.CreateJobRequest;
import dev.orbit.api.JobResponse;
import dev.orbit.domain.JobEntity;
import dev.orbit.domain.JobStatus;
import dev.orbit.repository.JobRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class JobService {
    private final JobRepository jobs;
    private final ObjectMapper objectMapper;
    private final JobCreationTransaction creation;

    public JobService(JobRepository jobs, ObjectMapper objectMapper, JobCreationTransaction creation) {
        this.jobs = jobs;
        this.objectMapper = objectMapper;
        this.creation = creation;
    }

    public JobResponse submit(CreateJobRequest request) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(request.payload());
        } catch (JacksonException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload must be valid JSON", exception);
        }
        if (payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1_048_576) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Job payload must not exceed 1 MiB");
        }
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<JobResponse> existing = findIdempotent(request, payloadJson);
            if (existing.isPresent()) return existing.get();
        }
        try {
            return creation.create(request, payloadJson);
        } catch (DataIntegrityViolationException race) {
            if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
                Optional<JobResponse> winner = findIdempotent(request, payloadJson);
                if (winner.isPresent()) return winner.get();
            }
            throw race;
        }
    }

    private Optional<JobResponse> findIdempotent(CreateJobRequest request, String payloadJson) {
        return jobs.findByTenantIdAndIdempotencyKey(request.tenantId(), request.idempotencyKey()).map(job -> {
            int requestedAttempts = request.maxAttempts() == null ? 5 : request.maxAttempts();
            boolean scheduledAtMatches = request.scheduledAt() == null || request.scheduledAt().equals(job.getScheduledAt());
            if (!job.getTaskType().equals(request.taskType()) || !job.getPayloadJson().equals(payloadJson)
                    || job.getPriority() != request.priority() || job.getMaxAttempts() != requestedAttempts
                    || !scheduledAtMatches) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Idempotency-Key already exists with a different task payload");
            }
            return JobResponse.from(job);
        });
    }

    @Transactional(readOnly = true)
    public JobResponse get(UUID id) {
        return jobs.findById(id).map(JobResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
    }

    @Transactional(readOnly = true)
    public List<JobResponse> list(String tenantId, JobStatus status) {
        boolean allTenants = tenantId == null || tenantId.isBlank();
        List<JobEntity> result;
        if (allTenants) {
            result = status == null ? jobs.findTop100ByOrderByCreatedAtDesc() : jobs.findTop100ByStatusOrderByCreatedAtDesc(status);
        } else {
            result = status == null ? jobs.findTop100ByTenantIdOrderByCreatedAtDesc(tenantId)
                                    : jobs.findTop100ByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
        }
        return result.stream().map(JobResponse::from).toList();
    }

    @Transactional
    public JobResponse cancel(UUID id) {
        JobEntity job = jobs.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        if (job.getStatus() != JobStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending jobs can be cancelled");
        }
        job.markCancelled();
        return JobResponse.from(job);
    }
}
