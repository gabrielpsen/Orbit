package dev.orbit.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orbit.worker")
public class WorkerProperties {
    private String id = "";
    private String hostname = "";
    private int concurrency = 8;
    private long pollIntervalMs = 350;
    private long heartbeatIntervalMs = 5000;
    private String schedulerHost = "localhost";
    private int schedulerGrpcPort = 9090;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }
    public int getConcurrency() { return concurrency; }
    public void setConcurrency(int concurrency) { this.concurrency = concurrency; }
    public long getPollIntervalMs() { return pollIntervalMs; }
    public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
    public long getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) { this.heartbeatIntervalMs = heartbeatIntervalMs; }
    public String getSchedulerHost() { return schedulerHost; }
    public void setSchedulerHost(String schedulerHost) { this.schedulerHost = schedulerHost; }
    public int getSchedulerGrpcPort() { return schedulerGrpcPort; }
    public void setSchedulerGrpcPort(int schedulerGrpcPort) { this.schedulerGrpcPort = schedulerGrpcPort; }
}
