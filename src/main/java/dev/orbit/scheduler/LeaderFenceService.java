package dev.orbit.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class LeaderFenceService {
    private final JdbcTemplate jdbc;

    public LeaderFenceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public long advanceEpoch() {
        Long epoch = jdbc.queryForObject(
                "UPDATE scheduler_fence SET epoch = epoch + 1, updated_at = NOW() WHERE singleton_id = 1 RETURNING epoch",
                Long.class);
        if (epoch == null) throw new IllegalStateException("Could not advance scheduler fencing epoch");
        return epoch;
    }

    @Transactional(readOnly = true)
    public long currentEpoch() {
        Long epoch = jdbc.queryForObject("SELECT epoch FROM scheduler_fence WHERE singleton_id = 1", Long.class);
        if (epoch == null) throw new IllegalStateException("Scheduler fencing row is missing");
        return epoch;
    }
}
