package dev.orbit.scheduler;

import dev.orbit.domain.WorkerEntity;
import dev.orbit.domain.WorkerStatus;
import dev.orbit.repository.JobRepository;
import dev.orbit.repository.WorkerRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class WorkerRegistryService {
    private final WorkerRepository workers;
    private final JobRepository jobs;
    private final StringRedisTemplate redis;
    private final SchedulerProperties properties;

    public WorkerRegistryService(WorkerRepository workers, JobRepository jobs, StringRedisTemplate redis,
                                 SchedulerProperties properties) {
        this.workers = workers;
        this.jobs = jobs;
        this.redis = redis;
        this.properties = properties;
    }

    @Transactional
    public void register(String workerId, String hostname, int maxConcurrency) {
        if (workerId == null || workerId.isBlank() || workerId.length() > 160) {
            throw new IllegalArgumentException("worker_id must contain 1 to 160 characters");
        }
        if (maxConcurrency < 1 || maxConcurrency > 1024) {
            throw new IllegalArgumentException("max_concurrency must be between 1 and 1024");
        }
        WorkerEntity worker = workers.findByIdForUpdate(workerId).orElse(null);
        if (worker == null) workers.save(new WorkerEntity(workerId, safeHostname(hostname), maxConcurrency));
        else worker.reRegister(safeHostname(hostname), maxConcurrency);
        markAlive(workerId);
    }

    @Transactional
    public boolean heartbeat(String workerId, int claimedActiveJobs) {
        WorkerEntity worker = workers.findByIdForUpdate(workerId).orElse(null);
        if (worker == null || worker.getStatus() == WorkerStatus.OFFLINE) return false;
        long active = jobs.countByWorkerIdAndStatus(workerId, dev.orbit.domain.JobStatus.RUNNING);
        worker.heartbeat((int) Math.min(Integer.MAX_VALUE, active));
        markAlive(workerId);
        return true;
    }

    @Transactional(readOnly = true)
    public List<WorkerEntity> list() {
        return workers.findAll().stream()
                .sorted((left, right) -> right.getLastHeartbeat().compareTo(left.getLastHeartbeat()))
                .toList();
    }

    @Transactional
    public int markStaleWorkersOffline(Instant cutoff) {
        int changed = 0;
        for (WorkerEntity worker : workers.findByStatusAndLastHeartbeatBefore(WorkerStatus.ONLINE, cutoff)) {
            boolean aliveKeyPresent;
            try {
                aliveKeyPresent = Boolean.TRUE.equals(redis.hasKey("orbit:worker:alive:" + worker.getWorkerId()));
            } catch (RuntimeException redisUnavailable) {
                aliveKeyPresent = false;
            }
            if (!aliveKeyPresent) {
                worker.markOffline();
                changed++;
            }
        }
        return changed;
    }

    private String safeHostname(String hostname) {
        if (hostname == null || hostname.isBlank()) return "unknown";
        return hostname.length() > 180 ? hostname.substring(0, 180) : hostname;
    }

    private void markAlive(String workerId) {
        redis.opsForValue().set("orbit:worker:alive:" + workerId, "1",
                java.time.Duration.ofSeconds(properties.getHeartbeatTimeoutSeconds()));
    }
}
