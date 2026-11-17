package dev.orbit.repository;

import dev.orbit.domain.JobEntity;
import dev.orbit.domain.JobStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<JobEntity, UUID> {
    Optional<JobEntity> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
    List<JobEntity> findByStatusAndScheduledAtLessThanEqualOrderByPriorityDescScheduledAtAscCreatedAtAsc(
            JobStatus status, Instant now, Pageable pageable);
    List<JobEntity> findByStatusAndLeaseExpiresAtBefore(JobStatus status, Instant now);
    List<JobEntity> findByWorkerIdAndStatus(String workerId, JobStatus status);
    List<JobEntity> findByWorkerIdAndStatusAndStartedAtAfter(String workerId, JobStatus status, Instant registeredAt);
    List<JobEntity> findTop100ByTenantIdOrderByCreatedAtDesc(String tenantId);
    List<JobEntity> findTop100ByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, JobStatus status);
    List<JobEntity> findTop100ByOrderByCreatedAtDesc();
    List<JobEntity> findTop100ByStatusOrderByCreatedAtDesc(JobStatus status);
    long countByStatus(JobStatus status);
    long countByStatusAndScheduledAtLessThanEqual(JobStatus status, Instant now);
    long countByTenantIdAndStatus(String tenantId, JobStatus status);
    long countByWorkerIdAndStatus(String workerId, JobStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from JobEntity j where j.id = :id")
    Optional<JobEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("select j.status as status, count(j) as total from JobEntity j group by j.status")
    List<Object[]> countGroupedByStatus();
}
