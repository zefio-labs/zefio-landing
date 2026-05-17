package io.zefio.core.engine.pool;

import io.zefio.core.util.MdcContextAwareExecutor;
import io.zefio.core.telemetry.provider.IMonitorPoolProvider;
import lombok.Getter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * SharedPools serves as a centralized provider for shared execution resources across the engine.
 * It manages scheduled executors, specialized IO pools, and MDC-wrapped executors to ensure
 * consistent context propagation during asynchronous processing.
 */
@Getter
public class SharedPools implements IMonitorPoolProvider {

    // Original ScheduledExecutorService used for core system tasks
    public final ScheduledExecutorService sharedScheduledPool;

    @Getter
    public final ScheduledExecutorService failsafe;

    // Raw ThreadPoolTaskExecutor used for monitoring and lifecycle management
    final ThreadPoolTaskExecutor sharedIoPool;

    // MDC-wrapped TaskExecutor used for actual task execution in the Flow pipeline
    @Getter
    final ExecutorService sharedMdcIoExecutor;

    public SharedPools(ScheduledExecutorService scheduledPool, ThreadPoolTaskExecutor ioPool, ScheduledExecutorService failsafe) {
        this.sharedScheduledPool = scheduledPool;
        this.failsafe = failsafe;
        this.sharedIoPool = ioPool;
        this.sharedMdcIoExecutor = new MdcContextAwareExecutor(ioPool.getThreadPoolExecutor());
    }

    @Override
    public ScheduledExecutorService getSharedScheduledPool() {
        return this.sharedScheduledPool;
    }

    @Override
    public ThreadPoolTaskExecutor getSharedIoPool() {
        return this.sharedIoPool;
    }
}
