package dev.orbit.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "orbit.role", havingValue = "worker")
@EnableConfigurationProperties(WorkerProperties.class)
public class WorkerConfiguration { }
