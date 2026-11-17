package dev.orbit.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "workers")
public class WorkerEntity {
    @Id
    @Column(name = "worker_id", length = 160)
    private String workerId;

    @Column(nullable = false, length = 180)
    private String hostname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkerStatus status;

    @Column(name = "max_concurrency", nullable = false)
    private int maxConcurrency;

    @Column(name = "active_jobs", nullable = false)
    private int activeJobs;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "last_heartbeat", nullable = false)
    private Instant lastHeartbeat;

    @Column(name = "completed_jobs", nullable = false)
    private long completedJobs;

    protected WorkerEntity() { }

    public WorkerEntity(String workerId, String hostname, int maxConcurrency) {
        this.workerId = workerId;
        this.hostname = hostname;
        this.maxConcurrency = Math.max(1, maxConcurrency);
        this.status = WorkerStatus.ONLINE;
        this.activeJobs = 0;
        this.registeredAt = Instant.now();
        this.lastHeartbeat = this.registeredAt;
        this.completedJobs = 0;
    }

    public void reRegister(String hostname, int maxConcurrency) {
        this.hostname = hostname;
        this.maxConcurrency = Math.max(1, maxConcurrency);
        this.registeredAt = Instant.now();
        this.lastHeartbeat = this.registeredAt;
        this.activeJobs = 0;
        this.status = WorkerStatus.ONLINE;
    }

    public void heartbeat(int activeJobs) {
        this.lastHeartbeat = Instant.now();
        this.activeJobs = Math.max(0, activeJobs);
        this.status = WorkerStatus.ONLINE;
    }

    public void markOffline() { this.status = WorkerStatus.OFFLINE; this.activeJobs = 0; }
    public void incrementCompleted() { this.completedJobs++; }
    public String getWorkerId() { return workerId; }
    public String getHostname() { return hostname; }
    public WorkerStatus getStatus() { return status; }
    public int getMaxConcurrency() { return maxConcurrency; }
    public int getActiveJobs() { return activeJobs; }
    public Instant getRegisteredAt() { return registeredAt; }
    public Instant getLastHeartbeat() { return lastHeartbeat; }
    public long getCompletedJobs() { return completedJobs; }
}
