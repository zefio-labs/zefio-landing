package io.zefio.core.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.Builder;
import lombok.Getter;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Context container for monitoring initialization.
 * Provides necessary registries and executors required for telemetry modules.
 */
@Getter
@Builder
public class MonitorInitContext {
    private final String flowName;
    private final String flowLabel;
    private final String moduleName;
    private final String moduleLabel;
    private final ScheduledExecutorService sharedScheduler;
    private final MeterRegistry meterRegistry;
}
