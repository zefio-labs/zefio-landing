package io.zefio.core.telemetry.provider;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Provider interface for accessing shared resource pools.
 * Supplies the scheduled executor for monitoring tasks and the global I/O pool
 * for resource tracking.
 */
public interface IMonitorPoolProvider {
    /** Returns the shared scheduler used for periodic monitoring tasks. */
    ScheduledExecutorService getSharedScheduledPool();

    /** Returns the global I/O pool that is the subject of monitoring. */
    ThreadPoolTaskExecutor getSharedIoPool();
}
