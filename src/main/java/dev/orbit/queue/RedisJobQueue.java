package dev.orbit.queue;

import dev.orbit.domain.JobEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class RedisJobQueue {
    private static final String TENANT_CLOCK = "orbit:fair:tenant-clock";
    private static final String GLOBAL_CLOCK = "orbit:fair:global-clock";

    private static final DefaultRedisScript<Long> ENQUEUE_SCRIPT = new DefaultRedisScript<>("""
        if redis.call('ZSCORE', KEYS[1], ARGV[1]) then return 0 end
        local tenantTime = tonumber(redis.call('HGET', KEYS[2], ARGV[2]) or '0')
        local globalTime = tonumber(redis.call('GET', KEYS[3]) or '0')
        local startTime = math.max(tenantTime, globalTime)
        local weight = math.max(1, tonumber(ARGV[3]))
        local finishTime = startTime + (1.0 / weight)
        redis.call('HSET', KEYS[2], ARGV[2], finishTime)
        redis.call('ZADD', KEYS[1], finishTime, ARGV[1])
        return 1
        """, Long.class);

    private static final DefaultRedisScript<Long> ADVANCE_CLOCK_SCRIPT = new DefaultRedisScript<>("""
        local current = tonumber(redis.call('GET', KEYS[1]) or '0')
        local candidate = tonumber(ARGV[1])
        if candidate > current then redis.call('SET', KEYS[1], candidate) end
        return 1
        """, Long.class);

    private final StringRedisTemplate redis;

    public RedisJobQueue(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void enqueue(JobEntity job, int tenantWeight) {
        redis.execute(ENQUEUE_SCRIPT,
                List.of(queueKey(job.getPriority()), TENANT_CLOCK, GLOBAL_CLOCK),
                job.getId().toString(), job.getTenantId(), Integer.toString(tenantWeight));
    }

    public Optional<String> popNext() {
        for (int priority = 9; priority >= 0; priority--) {
            ZSetOperations.TypedTuple<String> item = redis.opsForZSet().popMin(queueKey(priority));
            if (item != null && item.getValue() != null) {
                if (item.getScore() != null) {
                    redis.execute(ADVANCE_CLOCK_SCRIPT, List.of(GLOBAL_CLOCK), item.getScore().toString());
                }
                return Optional.of(item.getValue());
            }
        }
        return Optional.empty();
    }

    public void remove(String jobId, int priority) {
        redis.opsForZSet().remove(queueKey(priority), jobId);
    }

    private String queueKey(int priority) {
        return "orbit:queue:priority:" + priority;
    }
}
