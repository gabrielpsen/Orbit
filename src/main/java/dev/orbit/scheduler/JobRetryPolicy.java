package dev.orbit.scheduler;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class JobRetryPolicy {
    public Duration delayForAttempt(int attempt) {
        long exponent = Math.min(Math.max(attempt - 1, 0), 10);
        long baseMillis = Math.min(3_600_000L, 1_000L * (1L << exponent));
        double jitter = ThreadLocalRandom.current().nextDouble(0.75, 1.25);
        return Duration.ofMillis((long) (baseMillis * jitter));
    }
}
