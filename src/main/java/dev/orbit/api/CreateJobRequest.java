package dev.orbit.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

public record CreateJobRequest(
        @NotBlank @Size(max = 120) String tenantId,
        @NotBlank @Size(max = 80) String taskType,
        @NotNull Map<String, Object> payload,
        @Min(0) @Max(9) int priority,
        Instant scheduledAt,
        @Min(1) @Max(20) Integer maxAttempts,
        @Size(max = 180) String idempotencyKey
) {
    public CreateJobRequest withIdempotencyKey(String key) {
        return new CreateJobRequest(tenantId, taskType, payload, priority, scheduledAt, maxAttempts, key);
    }
}
