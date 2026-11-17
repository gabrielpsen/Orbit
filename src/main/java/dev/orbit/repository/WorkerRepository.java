package dev.orbit.repository;

import dev.orbit.domain.WorkerEntity;
import dev.orbit.domain.WorkerStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;

public interface WorkerRepository extends JpaRepository<WorkerEntity, String> {
    List<WorkerEntity> findByStatusOrderByLastHeartbeatDesc(WorkerStatus status);
    List<WorkerEntity> findByStatusAndLastHeartbeatBefore(WorkerStatus status, Instant cutoff);

    @Modifying
    @Transactional
    @Query("update WorkerEntity w set w.completedJobs = w.completedJobs + 1 where w.workerId = :workerId")
    int incrementCompleted(@Param("workerId") String workerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WorkerEntity w where w.workerId = :id")
    Optional<WorkerEntity> findByIdForUpdate(@Param("id") String workerId);
}
