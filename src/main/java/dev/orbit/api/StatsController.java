package dev.orbit.api;

import dev.orbit.domain.JobStatus;
import dev.orbit.repository.JobRepository;
import dev.orbit.repository.WorkerRepository;
import dev.orbit.scheduler.LeaderElector;
import dev.orbit.security.ApiTokenFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stats")
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class StatsController {
    private final JobRepository jobs;
    private final WorkerRepository workers;
    private final LeaderElector leader;

    public StatsController(JobRepository jobs, WorkerRepository workers, LeaderElector leader) {
        this.jobs = jobs;
        this.workers = workers;
        this.leader = leader;
    }

    @GetMapping
    public Map<String, Object> stats(HttpServletRequest request) {
        if (!Boolean.TRUE.equals(request.getAttribute(ApiTokenFilter.ADMIN_ATTRIBUTE))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin token required");
        }
        Map<JobStatus, Long> byStatus = new EnumMap<>(JobStatus.class);
        for (Object[] row : jobs.countGroupedByStatus()) {
            byStatus.put((JobStatus) row[0], (Long) row[1]);
        }
        return Map.of("jobs", byStatus, "workersTotal", workers.count(),
                "workersOnline", workers.findAll().stream().filter(w -> w.getStatus().name().equals("ONLINE")).count(),
                "schedulerLeader", leader.isLeader());
    }
}
