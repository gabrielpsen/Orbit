package dev.orbit.scheduler;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class LeaderElector {
    private static final String KEY = "orbit:scheduler:leader";
    private static final Duration LEASE = Duration.ofSeconds(10);
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('EXPIRE', KEYS[1], ARGV[2]) else return 0 end",
            Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end", Long.class);

    private final StringRedisTemplate redis;
    private final LeaderFenceService fence;
    private final String instanceId = UUID.randomUUID().toString();
    private volatile boolean leader;
    private volatile long fencingEpoch;

    public LeaderElector(StringRedisTemplate redis, LeaderFenceService fence) {
        this.redis = redis;
        this.fence = fence;
    }

    @Scheduled(fixedDelay = 3000, initialDelay = 1000)
    public synchronized void refreshLeadership() {
        try {
            if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(KEY, instanceId, LEASE))) {
                leader = false;
                fencingEpoch = fence.advanceEpoch();
                leader = true;
                return;
            }
            Long renewed = redis.execute(RENEW_SCRIPT, List.of(KEY), instanceId, Long.toString(LEASE.toSeconds()));
            if (renewed != null && renewed == 1L && fencingEpoch > 0) {
                leader = true;
            } else {
                leader = false;
                fencingEpoch = 0;
            }
        } catch (RuntimeException exception) {
            leader = false;
            fencingEpoch = 0;
            try { redis.execute(RELEASE_SCRIPT, List.of(KEY), instanceId); }
            catch (RuntimeException ignored) { /* Lease TTL remains the fail-safe. */ }
        }
    }

    public boolean isLeader() { return leader && fencingEpoch > 0; }
    public long fencingEpoch() { return isLeader() ? fencingEpoch : 0; }
    public String instanceId() { return instanceId; }
}
