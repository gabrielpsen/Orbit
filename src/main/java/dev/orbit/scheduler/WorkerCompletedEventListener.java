package dev.orbit.scheduler;

import dev.orbit.repository.WorkerRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class WorkerCompletedEventListener {
    private final WorkerRepository workers;
    public WorkerCompletedEventListener(WorkerRepository workers) { this.workers = workers; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onWorkerCompleted(WorkerCompletedEvent event) {
        workers.incrementCompleted(event.workerId());
    }
}
