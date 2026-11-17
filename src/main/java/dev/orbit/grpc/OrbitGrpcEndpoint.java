package dev.orbit.grpc;

import dev.orbit.scheduler.LeaderElector;
import dev.orbit.scheduler.SchedulerFacade;
import dev.orbit.scheduler.WorkerRegistryService;
import dev.orbit.security.OrbitSecuritySettings;
import dev.orbit.security.WorkerAuthInterceptor;
import dev.orbit.grpc.proto.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class OrbitGrpcEndpoint extends OrbitControlGrpc.OrbitControlImplBase {
    private final SchedulerFacade scheduler;
    private final WorkerRegistryService workers;
    private final LeaderElector leader;
    private final OrbitSecuritySettings security;

    public OrbitGrpcEndpoint(SchedulerFacade scheduler, WorkerRegistryService workers, LeaderElector leader,
                             OrbitSecuritySettings security) {
        this.scheduler = scheduler;
        this.workers = workers;
        this.leader = leader;
        this.security = security;
    }

    @Override
    public void registerWorker(RegisterWorkerRequest request, StreamObserver<RegisterWorkerResponse> observer) {
        try {
            if (request.getWorkerId().isBlank()) throw Status.INVALID_ARGUMENT.withDescription("worker_id is required").asRuntimeException();
            requireAuthenticatedIdentity(request.getWorkerId());
            workers.register(request.getWorkerId(), request.getHostname(), request.getMaxConcurrency());
            observer.onNext(RegisterWorkerResponse.newBuilder().setAccepted(true).setHeartbeatIntervalSeconds(5).build());
            observer.onCompleted();
        } catch (StatusRuntimeException exception) {
            observer.onError(exception);
        } catch (IllegalArgumentException exception) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription(exception.getMessage()).asRuntimeException());
        } catch (RuntimeException exception) {
            observer.onError(Status.INTERNAL.withDescription("Worker registration failed").asRuntimeException());
        }
    }

    @Override
    public void heartbeat(HeartbeatRequest request, StreamObserver<HeartbeatResponse> observer) {
        try {
            requireAuthenticatedIdentity(request.getWorkerId());
        } catch (RuntimeException exception) {
            observer.onError(exception);
            return;
        }
        try {
            boolean accepted = workers.heartbeat(request.getWorkerId(), request.getActiveJobs());
            observer.onNext(HeartbeatResponse.newBuilder().setAccepted(accepted).build());
            observer.onCompleted();
        } catch (RuntimeException exception) {
            observer.onError(Status.INTERNAL.withDescription("Worker heartbeat failed").asRuntimeException());
        }
    }

    @Override
    public void pollJob(PollJobRequest request, StreamObserver<PollJobResponse> observer) {
        if (request.getWorkerId().isBlank()) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription("worker_id is required").asRuntimeException());
            return;
        }
        try {
            requireAuthenticatedIdentity(request.getWorkerId());
        } catch (RuntimeException exception) {
            observer.onError(exception);
            return;
        }
        if (!leader.isLeader()) {
            observer.onError(Status.UNAVAILABLE.withDescription("No active scheduler leader").asRuntimeException());
            return;
        }
        var result = scheduler.poll(request.getWorkerId(), leader.fencingEpoch());
        PollJobResponse.Builder response = PollJobResponse.newBuilder().setHasJob(result.isPresent());
        result.ifPresent(job -> response
                .setJobId(job.getId().toString())
                .setTenantId(job.getTenantId())
                .setTaskType(job.getTaskType())
                .setPayloadJson(job.getPayloadJson())
                .setAttempt(job.getAttempts())
                .setMaxAttempts(job.getMaxAttempts())
                .setLeaseToken(job.getLeaseToken().toString()));
        observer.onNext(response.build());
        observer.onCompleted();
    }

    @Override
    public void renewJobLease(RenewJobLeaseRequest request, StreamObserver<RenewJobLeaseResponse> observer) {
        try {
            requireAuthenticatedIdentity(request.getWorkerId());
            boolean accepted = scheduler.renewJobLease(request.getWorkerId(), UUID.fromString(request.getJobId()),
                    UUID.fromString(request.getLeaseToken()));
            observer.onNext(RenewJobLeaseResponse.newBuilder().setAccepted(accepted).build());
            observer.onCompleted();
        } catch (StatusRuntimeException exception) {
            observer.onError(exception);
        } catch (IllegalArgumentException exception) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription("job_id and lease_token must be UUIDs").asRuntimeException());
        }
    }

    @Override
    public void completeJob(CompleteJobRequest request, StreamObserver<CompleteJobResponse> observer) {
        try {
            requireAuthenticatedIdentity(request.getWorkerId());
            SchedulerFacade.CompletionResult result = scheduler.complete(request.getWorkerId(),
                    UUID.fromString(request.getJobId()), UUID.fromString(request.getLeaseToken()), request.getSuccess(),
                    request.getOutputJson(), request.getErrorMessage());
            observer.onNext(CompleteJobResponse.newBuilder().setAccepted(result.accepted()).setState(result.state()).build());
            observer.onCompleted();
        } catch (StatusRuntimeException exception) {
            observer.onError(exception);
        } catch (IllegalArgumentException exception) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription("job_id and lease_token must be UUIDs").asRuntimeException());
        }
    }

    private void requireAuthenticatedIdentity(String claimedWorkerId) {
        if (!security.isProduction()) return;
        String authenticatedWorkerId = WorkerAuthInterceptor.AUTHENTICATED_WORKER_ID.get();
        if (authenticatedWorkerId == null || !authenticatedWorkerId.equals(claimedWorkerId)) {
            throw Status.PERMISSION_DENIED.withDescription("worker_id does not match the authenticated client certificate").asRuntimeException();
        }
    }
}
