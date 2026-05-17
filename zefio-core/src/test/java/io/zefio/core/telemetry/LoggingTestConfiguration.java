package io.zefio.core.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.zefio.core.config.monitor.MonitorProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Spring Boot configuration class for telemetry and logging tests.
 * Provides necessary beans such as MeterRegistry, MonitorProperties, and TaskScheduler
 * to resolve dependency injection requirements during isolated testing.
 */
@Configuration
@SpringBootApplication(scanBasePackages = "io.zefio.core.telemetry")
public class LoggingTestConfiguration {

    /**
     * Registers a SimpleMeterRegistry bean to resolve NoSuchBeanDefinitionException
     * for metrics collection during tests.
     */
    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    /**
     * Registers MonitorProperties bean to resolve UnsatisfiedDependencyException
     * for telemetry module initialization.
     */
    @Bean
    public MonitorProperties monitorProperties() {
        return new MonitorProperties();
    }

    /**
     * Registers a TaskScheduler bean to resolve dependencies for scheduled tasks,
     * such as the metrics reset scheduler.
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("test-scheduler-");
        scheduler.initialize();
        return scheduler;
    }
}
