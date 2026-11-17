package dev.orbit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenant_quotas")
public class TenantQuotaEntity {
    @Id
    @Column(name = "tenant_id", length = 120)
    private String tenantId;

    @Column(name = "max_concurrent_jobs", nullable = false)
    private int maxConcurrentJobs;

    @Column(nullable = false)
    private int weight;

    protected TenantQuotaEntity() { }

    public TenantQuotaEntity(String tenantId, int maxConcurrentJobs, int weight) {
        this.tenantId = tenantId;
        this.maxConcurrentJobs = maxConcurrentJobs;
        this.weight = weight;
    }

    public String getTenantId() { return tenantId; }
    public int getMaxConcurrentJobs() { return maxConcurrentJobs; }
    public int getWeight() { return weight; }
}
