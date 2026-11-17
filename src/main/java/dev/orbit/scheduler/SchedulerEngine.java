package dev.orbit.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class SchedulerEngine {
    private final SchedulerFacade scheduler;
    private final LeaderElector leader;

    public SchedulerEngine(SchedulerFacade scheduler, LeaderElector leader) {
        this.scheduler = scheduler;
        this.leader = leader;
    }

    @Scheduled(fixedDelayString = "${orbit.scheduler.dispatch-interval-ms:500}")
    public void dispatch() {
        if (leader.isLeader()) scheduler.publishDueJobs();
    }

    @Scheduled(fixedDelayString = "${orbit.scheduler.recovery-interval-ms:5000}")
    public void recover() {
        if (leader.isLeader()) {
            scheduler.recoverExpiredLeases();
            scheduler.markStaleWorkersOffline();
        }
    }
}
