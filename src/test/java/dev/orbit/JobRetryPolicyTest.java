package dev.orbit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.orbit.scheduler.JobRetryPolicy;
import org.junit.jupiter.api.Test;

class JobRetryPolicyTest {
    private final JobRetryPolicy policy = new JobRetryPolicy();

    @Test
    void appliesExponentialBackoffWithJitter() {
        var first = policy.delayForAttempt(1);
        var second = policy.delayForAttempt(2);
        assertThat(first.toMillis()).isBetween(750L, 1250L);
        assertThat(second.toMillis()).isBetween(1500L, 2500L);
    }
}
