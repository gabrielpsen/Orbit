package dev.orbit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.orbit.queue.RedisJobQueue;
import dev.orbit.scheduler.JobService;
import dev.orbit.scheduler.LeaderElector;
import dev.orbit.security.ApiTokenFilter;
import dev.orbit.worker.WorkerRuntime;
import javax.sql.DataSource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "orbit.role=worker",
        "orbit.security.environment=development",
        "orbit.worker.scheduler-host=127.0.0.1",
        "orbit.worker.scheduler-grpc-port=1",
        "orbit.worker.heartbeat-interval-ms=60000",
        "orbit.worker.poll-interval-ms=60000"
})
@ContextConfiguration(initializers = WorkerRoleEnvironmentInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WorkerRoleIsolationTest {
    @Autowired ApplicationContext context;

    @Test
    void workerRoleDoesNotCreateDatabaseOrSchedulerControlPlaneBeans() {
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(context.getBeansOfType(RedisConnectionFactory.class)).isEmpty();
        assertThat(context.getBeansOfType(JobService.class)).isEmpty();
        assertThat(context.getBeansOfType(RedisJobQueue.class)).isEmpty();
        assertThat(context.getBeansOfType(LeaderElector.class)).isEmpty();
        assertThat(context.getBeansOfType(dev.orbit.scheduler.SchedulerFacade.class)).isEmpty();
        assertThat(context.getBeansOfType(ApiTokenFilter.class)).isEmpty();
        assertThat(context.getBeansOfType(WorkerRuntime.class)).hasSize(1);
    }
}
