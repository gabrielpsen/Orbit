package dev.orbit.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orbit.scheduler")
public class SchedulerProperties {
    private long dispatchIntervalMs = 500;
    private long leaseSeconds = 30;
    private long heartbeatTimeoutSeconds = 20;
    private long recoveryIntervalMs = 5000;
    private int defaultTenantConcurrency = 10;

    public long getDispatchIntervalMs() { return dispatchIntervalMs; }
    public void setDispatchIntervalMs(long value) { this.dispatchIntervalMs = value; }
    public long getLeaseSeconds() { return leaseSeconds; }
    public void setLeaseSeconds(long value) { this.leaseSeconds = value; }
    public long getHeartbeatTimeoutSeconds() { return heartbeatTimeoutSeconds; }
    public void setHeartbeatTimeoutSeconds(long value) { this.heartbeatTimeoutSeconds = value; }
    public long getRecoveryIntervalMs() { return recoveryIntervalMs; }
    public void setRecoveryIntervalMs(long value) { this.recoveryIntervalMs = value; }
    public int getDefaultTenantConcurrency() { return defaultTenantConcurrency; }
    public void setDefaultTenantConcurrency(int value) { this.defaultTenantConcurrency = value; }
}
