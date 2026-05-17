package io.zefio.core.engine.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;

/**
 * DynamicPoolScaler provides an automated scaling mechanism for thread pools.
 * It monitors usage metrics at fixed intervals and adjusts the pool size dynamically
 * based on the following logic:
 * 1. Scale-Up: If $currentUsage \ge scaleUpThreshold$ and $currentCore < maxSize$.
 * 2. Scale-Down: If $currentUsage < scaleDownThreshold$ and $currentCore > minSize$.
 */
public class DynamicPoolScaler {

    private static final Logger log = LoggerFactory.getLogger(DynamicPoolScaler.class);

    /**
     * Registers an auto-scaling task to the scheduler.
     *
     * @param scheduler          Shared scheduled executor pool
     * @param executor           Target thread pool to scale
     * @param logPrefix          Identifier for log output (e.g., Shared-IO-Scale)
     * @param intervalSec        Check interval in seconds
     * @param minSize            Minimum (base) core pool size
     * @param maxSize            Maximum pool size limit
     * @param scaleUpThreshold   Threshold to trigger expansion (e.g., 0.8 for 80%)
     * @param scaleDownThreshold Threshold to trigger contraction (e.g., 0.3 for 30%)
     * @param usageSupplier      Function calculating current usage (0.0 to 1.0)
     * @param scaleUpMath        Logic to determine next size during scale-up
     * @param scaleDownMath      Logic to determine next size during scale-down
     */
    public static ScheduledFuture<?> start(
            ScheduledExecutorService scheduler,
            ThreadPoolTaskExecutor executor,
            String logPrefix,
            int intervalSec,
            int minSize,
            int maxSize,
            double scaleUpThreshold,
            double scaleDownThreshold,
            DoubleSupplier usageSupplier,
            IntUnaryOperator scaleUpMath,
            IntUnaryOperator scaleDownMath) {

        return scheduler.scheduleAtFixedRate(() -> {
            try {
                double currentUsage = usageSupplier.getAsDouble();
                int currentCore = executor.getCorePoolSize();

                // 1. [Scale-Up] Expand if usage exceeds threshold and size is below max
                if (currentUsage >= scaleUpThreshold && currentCore < maxSize) {
                    int nextSize = Math.min(scaleUpMath.applyAsInt(currentCore), maxSize);

                    if (nextSize > currentCore) {
                        executor.setMaxPoolSize(nextSize);  // Increase ceiling first
                        executor.setCorePoolSize(nextSize); // Increase floor second
                        executor.getThreadPoolExecutor().prestartAllCoreThreads();

                        log.info("[{}] UP: Usage={}% (Threshold={}) | Core {} -> {}",
                                logPrefix, String.format("%.1f", currentUsage * 100), scaleUpThreshold, currentCore, nextSize);
                    }
                }
                // 2. [Scale-Down] Contract if usage is significantly low
                else if (currentUsage < scaleDownThreshold && currentCore > minSize) {
                    int nextSize = Math.max(scaleDownMath.applyAsInt(currentCore), minSize);

                    if (nextSize < currentCore) {
                        executor.setCorePoolSize(nextSize); // Decrease floor first
                        executor.setMaxPoolSize(nextSize);  // Decrease ceiling second
                        log.info("[{}] DOWN: Usage={}% | Core {} -> {}",
                                logPrefix, String.format("%.1f", currentUsage * 100), currentCore, nextSize);
                    }
                }
            } catch (Exception e) {
                log.error("[{}] Auto-scaling loop error", logPrefix, e);
            }
        }, intervalSec, intervalSec, TimeUnit.SECONDS);
    }
}
