package dev.orbit.worker;

import dev.orbit.grpc.proto.*;
import dev.orbit.security.OrbitSecuritySettings;
import io.grpc.StatusRuntimeException;
import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "orbit.role", havingValue = "worker")
public class WorkerRuntime {
    private static final Logger log = LoggerFactory.getLogger(WorkerRuntime.class);
    private final OrbitControlGrpc.OrbitControlBlockingStub stub;
    private final WorkerProperties properties;
    private final TaskExecutor taskExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean registered = new AtomicBoolean(false);
    private final Semaphore permits;
    private final Map<String, ActiveTask> activeTasks = new ConcurrentHashMap<>();
    private final long maxRuntimeSeconds;
    private ExecutorService jobsExecutor;
    private ScheduledExecutorService heartbeatExecutor;
    private Thread poller;
    private final String workerId;
    private final String hostname;

    public WorkerRuntime(OrbitControlGrpc.OrbitControlBlockingStub stub, WorkerProperties properties,
                         TaskExecutor taskExecutor, OrbitSecuritySettings security,
                         @Value("${orbit.worker.max-runtime-seconds:900}") long maxRuntimeSeconds) {
        this.stub = stub;
        this.properties = properties;
        this.taskExecutor = taskExecutor;
        if (properties.getConcurrency() < 1 || properties.getConcurrency() > 1024) {
            throw new IllegalStateException("ORBIT_WORKER_CONCURRENCY must be between 1 and 1024");
        }
        this.permits = new Semaphore(properties.getConcurrency());
        if (security.isProduction() && (properties.getId() == null || !properties.getId().matches("[A-Za-z0-9._-]{1,120}"))) {
            throw new IllegalStateException("Production workers must set ORBIT_WORKER_ID to match the URI SAN in their client certificate");
        }
        this.workerId = properties.getId() == null || properties.getId().isBlank()
                ? UUID.randomUUID().toString() : properties.getId();
        this.hostname = resolveHostname(properties.getHostname());
        this.maxRuntimeSeconds = Math.max(10, Math.min(maxRuntimeSeconds, 86_400));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        jobsExecutor = Executors.newVirtualThreadPerTaskExecutor();
        heartbeatExecutor = Executors.newScheduledThreadPool(2, Thread.ofVirtual().factory());
        heartbeatExecutor.scheduleAtFixedRate(this::heartbeat, 0,
                Math.max(1000, properties.getHeartbeatIntervalMs()), TimeUnit.MILLISECONDS);
        poller = Thread.ofVirtual().name("orbit-poller-" + workerId).start(this::pollLoop);
        log.info("Orbit worker {} starting on {} with concurrency {}", workerId, hostname, properties.getConcurrency());
    }

    private void pollLoop() {
        while (running.get()) {
            if (!registered.get()) {
                register();
                pause(1000);
                continue;
            }
            if (!permits.tryAcquire()) {
                pause(25);
                continue;
            }
            try {
                PollJobResponse response = stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                        .pollJob(PollJobRequest.newBuilder().setWorkerId(workerId).build());
                if (!response.getHasJob()) {
                    permits.release();
                    pause(properties.getPollIntervalMs());
                    continue;
                }
                jobsExecutor.submit(() -> execute(response));
            } catch (StatusRuntimeException exception) {
                permits.release();
                registered.set(false);
                log.warn("Scheduler poll failed: {}", exception.getStatus());
                pause(1000);
            } catch (RuntimeException exception) {
                permits.release();
                log.error("Unexpected worker polling error", exception);
                pause(1000);
            }
        }
    }

    private synchronized void register() {
        if (registered.get()) return;
        try {
            RegisterWorkerResponse response = stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                    .registerWorker(RegisterWorkerRequest.newBuilder()
                            .setWorkerId(workerId).setHostname(hostname)
                            .setMaxConcurrency(properties.getConcurrency())
                            .addCapabilities("echo").addCapabilities("sleep").addCapabilities("sum").build());
            registered.set(response.getAccepted());
            if (registered.get()) log.info("Worker {} registered with scheduler", workerId);
        } catch (StatusRuntimeException exception) {
            log.warn("Worker registration failed: {}", exception.getStatus());
        }
    }

    private void heartbeat() {
        if (!running.get()) return;
        try {
            if (!registered.get()) {
                register();
                return;
            }
            HeartbeatResponse response = stub.withDeadlineAfter(4, TimeUnit.SECONDS)
                    .heartbeat(HeartbeatRequest.newBuilder().setWorkerId(workerId)
                            .setActiveJobs(activeTasks.size()).build());
            if (!response.getAccepted()) {
                registered.set(false);
                return;
            }
            // Renew leases in parallel so one slow call can't expire the rest.
            for (ActiveTask task : activeTasks.values()) {
                if (task.timedOut().get() || !task.renewing().compareAndSet(false, true)) continue;
                try {
                    jobsExecutor.submit(() -> renewLease(task));
                } catch (RuntimeException rejected) {
                    task.renewing().set(false);
                    if (running.get()) log.warn("Could not schedule lease renewal for job {}", task.jobId());
                }
            }
        } catch (StatusRuntimeException exception) {
            registered.set(false);
            log.warn("Worker heartbeat failed: {}", exception.getStatus());
        }
    }

    private void execute(PollJobResponse job) {
        Thread current = Thread.currentThread();
        ActiveTask active = new ActiveTask(job.getJobId(), job.getLeaseToken(), current,
                new AtomicBoolean(false), new AtomicBoolean(false));
        activeTasks.put(job.getJobId(), active);
        ScheduledFuture<?> timeout = heartbeatExecutor.schedule(() -> {
            if (activeTasks.get(job.getJobId()) == active && active.timedOut().compareAndSet(false, true)) {
                log.warn("Job {} exceeded max runtime of {} seconds; interrupting attempt and allowing its lease to expire", job.getJobId(), maxRuntimeSeconds);
                current.interrupt();
            }
        }, maxRuntimeSeconds, TimeUnit.SECONDS);
        try {
            String output = taskExecutor.execute(job.getTaskType(), job.getPayloadJson());
            if (active.timedOut().get()) throw new InterruptedException("Task exceeded maximum runtime");
            complete(job.getJobId(), job.getLeaseToken(), true, output, "");
        } catch (Exception exception) {
            String message = exception instanceof InterruptedException
                    ? "Task interrupted or exceeded maximum runtime"
                    : (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            if (message.length() > 2000) message = message.substring(0, 2000);
            complete(job.getJobId(), job.getLeaseToken(), false, "{}", message);
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            timeout.cancel(false);
            activeTasks.remove(job.getJobId(), active);
            Thread.interrupted();
            permits.release();
        }
    }

    private void renewLease(ActiveTask task) {
        try {
            RenewJobLeaseResponse renewed = stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .renewJobLease(RenewJobLeaseRequest.newBuilder().setWorkerId(workerId)
                            .setJobId(task.jobId()).setLeaseToken(task.leaseToken()).build());
            if (!renewed.getAccepted() && activeTasks.get(task.jobId()) == task
                    && task.thread().isAlive() && task.timedOut().compareAndSet(false, true)) {
                log.warn("Lease renewal rejected for job {}; interrupting work to avoid stale execution", task.jobId());
                task.thread().interrupt();
            }
        } catch (StatusRuntimeException exception) {
            log.warn("Lease renewal failed for job {}: {}", task.jobId(), exception.getStatus());
        } finally {
            task.renewing().set(false);
        }
    }

    private void complete(String jobId, String leaseToken, boolean success, String output, String error) {
        for (int attempt = 0; attempt < 5 && running.get(); attempt++) {
            try {
                CompleteJobResponse response = stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                        .completeJob(CompleteJobRequest.newBuilder().setWorkerId(workerId).setJobId(jobId)
                                .setLeaseToken(leaseToken).setSuccess(success).setOutputJson(output)
                                .setErrorMessage(error).build());
                if (!response.getAccepted()) log.warn("Completion for job {} was rejected (state={})", jobId, response.getState());
                return;
            } catch (StatusRuntimeException exception) {
                log.warn("Completion attempt {} for job {} failed: {}", attempt + 1, jobId, exception.getStatus());
                pause(500L * (attempt + 1));
            }
        }
        log.error("Could not report completion for job {}. Scheduler recovery will handle the expired lease", jobId);
    }

    private void pause(long millis) {
        try { Thread.sleep(Math.max(1, millis)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }

    private String resolveHostname(String configured) {
        if (configured != null && !configured.isBlank()) return configured;
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (Exception ignored) { return "unknown"; }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (poller != null) poller.interrupt();
        activeTasks.values().forEach(task -> task.thread().interrupt());
        if (heartbeatExecutor != null) heartbeatExecutor.shutdownNow();
        if (jobsExecutor != null) jobsExecutor.shutdownNow();
    }

    private record ActiveTask(String jobId, String leaseToken, Thread thread,
                              AtomicBoolean timedOut, AtomicBoolean renewing) { }
}
