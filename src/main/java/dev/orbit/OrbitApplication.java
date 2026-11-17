package dev.orbit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrbitApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(OrbitApplication.class);
        application.addInitializers(new WorkerRoleEnvironmentInitializer());
        application.run(args);
    }
}
