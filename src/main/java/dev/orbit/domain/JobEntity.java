package dev.orbit.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jobs", indexes = {
        @Index(name = "idx_jobs_dispatch", columnList = "status, priority, scheduled_at"),
        @Index(name = "idx_jobs_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_jobs_worker_lease", columnList = "worker_id, lease_expires_at")
}, uniqueConstraints = @UniqueConstraint(name = "uq_jobs_tenant_idempotency", columnNames = {"tenant_id", "idempotency_key"}))
public class JobEntity {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 120)
    private String tenantId;

    @Column(name = "task_type", nullable = false, length = 80)
    private String taskType;

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;

    @Column(nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "idempotency_key", length = 180)
    private String idempotencyKey;

    @Column(name = "worker_id", length = 160)
    private String workerId;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "lease_token")
    private UUID leaseToken;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "output_json", columnDefinition = "text")
    private String outputJson;

    protected JobEntity() { }

    public static JobEntity create(String tenantId, String taskType, String payloadJson, int priority,
                                   Instant scheduledAt, int maxAttempts, String idempotencyKey) {
        JobEntity job = new JobEntity();
        job.id = UUID.randomUUID();
        job.tenantId = tenantId;
        job.taskType = taskType;
        job.payloadJson = payloadJson;
        job.priority = priority;
        job.status = JobStatus.PENDING;
        job.scheduledAt = scheduledAt;
        job.createdAt = Instant.now();
        job.updatedAt = job.createdAt;
        job.attempts = 0;
        job.maxAttempts = maxAttempts;
        job.idempotencyKey = idempotencyKey;
        return job;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    public void markRunning(String workerId, Instant leaseExpiresAt) {
        this.status = JobStatus.RUNNING;
        this.workerId = workerId;
        this.leaseExpiresAt = leaseExpiresAt;
        this.leaseToken = UUID.randomUUID();
        this.startedAt = Instant.now();
        this.finishedAt = null;
        this.attempts++;
        this.updatedAt = Instant.now();
        this.lastError = null;
    }

    public void extendLease(Instant leaseExpiresAt) {
        if (this.status == JobStatus.RUNNING) {
            this.leaseExpiresAt = leaseExpiresAt;
            this.updatedAt = Instant.now();
        }
    }

    public void markSucceeded(String outputJson) {
        this.status = JobStatus.SUCCEEDED;
        this.outputJson = outputJson;
        this.finishedAt = Instant.now();
        clearLease();
    }

    public void markRetry(String error, Instant retryAt) {
        this.status = JobStatus.PENDING;
        this.lastError = error;
        this.scheduledAt = retryAt;
        clearLease();
    }

    public void markDead(String error) {
        this.status = JobStatus.DEAD;
        this.lastError = error;
        this.finishedAt = Instant.now();
        clearLease();
    }

    public void markCancelled() {
        this.status = JobStatus.CANCELLED;
        this.finishedAt = Instant.now();
        clearLease();
    }

    private void clearLease() {
        this.workerId = null;
        this.leaseExpiresAt = null;
        this.leaseToken = null;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getTaskType() { return taskType; }
    public String getPayloadJson() { return payloadJson; }
    public int getPriority() { return priority; }
    public JobStatus getStatus() { return status; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public int getAttempts() { return attempts; }
    public int getMaxAttempts() { return maxAttempts; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getWorkerId() { return workerId; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public UUID getLeaseToken() { return leaseToken; }
    public String getLastError() { return lastError; }
    public String getOutputJson() { return outputJson; }
}
