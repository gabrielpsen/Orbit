package dev.orbit.repository;

import dev.orbit.domain.TenantQuotaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantQuotaRepository extends JpaRepository<TenantQuotaEntity, String> {
    @Modifying
    @Query(value = "INSERT INTO tenant_quotas (tenant_id, max_concurrent_jobs, weight) VALUES (:tenantId, :maxConcurrent, 1) ON CONFLICT (tenant_id) DO NOTHING", nativeQuery = true)
    int createDefaultIfAbsent(@Param("tenantId") String tenantId, @Param("maxConcurrent") int maxConcurrent);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from TenantQuotaEntity q where q.tenantId = :tenantId")
    Optional<TenantQuotaEntity> findByTenantIdForUpdate(@Param("tenantId") String tenantId);
}
