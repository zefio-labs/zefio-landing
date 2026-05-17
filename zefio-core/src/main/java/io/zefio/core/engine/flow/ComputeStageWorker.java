package io.zefio.core.engine.flow;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.payload.Payload;
import io.zefio.core.config.flow.FlowOptions;
import io.zefio.core.engine.pool.DynamicPoolScaler;
import io.zefio.core.telemetry.provider.IQueueStatusProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Core worker responsible for the compute stage of the pipeline.
 * It manages an internal blocking queue, ensures all data is processed during shutdown,
 * and handles dynamic auto-scaling of the underlying thread pool based on queue occupancy.
 */
public class ComputeStageWorker implements IQueueStatusProvider {
    private final Logger log = LoggerFactory.getLogger(getClass());

    // Prefix for thread naming conventions
    private static final String CPU_WORKER = "-cpu-worker";

    private final String flowName;
    private final FlowErrorHandler errorHandler;
    private final FlowOptions options;
    private final ScheduledExecutorService sharedScheduledPool;
    private final BlockingQueue<ExchangeContext> queue;
    private final int queueCapacity;

    private volatile boolean isRunning = false;
    private Thread workerThread;

    public ComputeStageWorker(FlowInitContext context, FlowErrorHandler errorHandler) {
        this.flowName = context.getFlowName();
        this.errorHandler = errorHandler;
        this.options = context.getOptions();
        this.sharedScheduledPool = context.getSharedPools().getSharedScheduledPool();
        this.queueCapacity = options.getCpuQueue().getCapacity();
        this.queue = new LinkedBlockingQueue<>(queueCapacity);
    }

    /**
     * Start the execution engine (Worker Thread and Auto-scaling)
     */
    public void start(Executor flowExecutor, ThreadPoolTaskExecutor rawFlowPool, Consumer<Payload> Worker) {
        this.isRunning = true;

        // 1. Define and start the Consumer (Worker) Thread
        this.workerThread = new Thread(() -> {
            log.info("CpuStage Worker thread [{}] started.", Thread.currentThread().getName());

            // [Standard] Continue processing until the queue is empty, even if isRunning is false (Graceful Shutdown).
            while (isRunning || !queue.isEmpty()) {
                try {
                    // [Optimization: Short-Polling]
                    // Wait 100ms during normal operation to prevent CPU waste.
                    // Switch to 0ms (Non-blocking) upon shutdown to drain the queue rapidly.
                    ExchangeContext context = queue.poll(isRunning ? 100 : 0, TimeUnit.MILLISECONDS);

                    if (context != null) {
                        long stayTime = System.currentTimeMillis() - context.enqueuedTime;
                        context.payload.addQueueWaitTime(stayTime);
                        checkStayTime(context.payload.getTrxID(), stayTime);

                        try {
                            // Call the Consumer (FilterPipelineProcessor)
                            flowExecutor.execute(() -> Worker.accept(context.payload));
                        } catch (RejectedExecutionException re) {
                            // Wrap rejection with a clear message when the thread pool is exhausted.
                            String cleanMsg = String.format("Worker Thread Pool Exhausted (Active=%d, Max=%d)",
                                    rawFlowPool.getActiveCount(), rawFlowPool.getMaxPoolSize());
                            reject(context.payload, cleanMsg, FlowResultStatus.SYSTEM_BUSY);
                        }
                    } else if (!isRunning) {
                        // Exit the loop only when the queue is completely empty and in a stopped state.
                        break;
                    }
                } catch (InterruptedException e) {
                    // Handle OS-level forced interrupts; normal graceful shutdown should not trigger this.
                    log.warn("[{}] CpuStage Worker forcibly interrupted. Exiting.", flowName);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("[{}] CpuStage Worker loop error", flowName, e);
                }
            }
            log.info("CpuStage Worker thread [{}] finished.", Thread.currentThread().getName());
        }, flowName + CPU_WORKER);
        this.workerThread.setDaemon(true);
        this.workerThread.start();

        // 2. Start Auto-scaling monitoring
        FlowOptions.AutoScalingOptions scaleConfig = options.getThreadPool().getAutoScaling();
        if (scaleConfig.isEnabled()) {
            log.info("[{}] Flow CPU Auto-scaling is enabled. Threshold: {}, Interval: {}s",
                    flowName, scaleConfig.getThreshold(), scaleConfig.getCheckInterval());
            startAutoScaling(rawFlowPool);
        }
    }

    public void submit(Payload payload) {
        if (!isRunning) {
            reject(payload, "Flow is shutting down", FlowResultStatus.SYSTEM_SHUTDOWN);
            return;
        }

        boolean accepted = queue.offer(new ExchangeContext(payload));
        if (!accepted) {
            reject(payload, "System Busy: CPU Queue capacity reached", FlowResultStatus.QUEUE_CAPACITY_EXCEEDED);
        }
    }

    /**
     * Rejects an event and processes it through the error pipeline.
     * Uses explicit FlowResultStatus for visibility on monitoring dashboards.
     */
    private void reject(Payload payload, String message, FlowResultStatus status) {
        FlowException ex = new FlowException(status, message);

        log.warn("[{}] CRITICAL: Rejecting Event TID: {}. Reason: {}", flowName, payload.getTrxID(), message);

        // Execute the error pipeline immediately in the current thread (Runnable::run) since the queue is full.
        errorHandler.handleError(ex, payload, payload.getCallback(), 0, Runnable::run);
    }

    private void startAutoScaling(ThreadPoolTaskExecutor executor) {
        int minSize = options.getThreadPool().getCorePoolSize();
        int maxSize = executor.getMaxPoolSize();
        FlowOptions.AutoScalingOptions scaleConfig = options.getThreadPool().getAutoScaling();

        DynamicPoolScaler.start(
                sharedScheduledPool,
                executor,
                flowName + "-CpuScale",
                scaleConfig.getCheckInterval(),
                minSize,
                maxSize,
                scaleConfig.getThreshold(),
                0.1, // Down Threshold
                () -> {
                    int capacity = options.getCpuQueue().getCapacity();
                    return capacity > 0 ? (double) queue.size() / capacity : 0.0;
                },
                currentCore -> currentCore + scaleConfig.getScaleUpStep(),
                currentCore -> currentCore - scaleConfig.getScaleDownStep()
        );
    }

    private void checkStayTime(String trxId, long stayTime) {
        if (stayTime > 1000) {
            log.warn("[{}] High CPU Queue Latency: {}ms for TID: {}", flowName, stayTime, trxId);
        }
    }

    public void stop() {
        // 1. Lower the flag to signal non-blocking poll mode.
        this.isRunning = false;
        if (workerThread != null) {
            try {
                // 2. Wait for the worker thread to finish processing remaining data (100% data guarantee).
                workerThread.join(5000);
            } catch (InterruptedException ignored) {}
        }
    }

    public boolean isQueueEmpty() { return queue.isEmpty(); }

    @Override public int getQueueSize() { return queue.size(); }
    @Override public int getQueueCapacity() { return queueCapacity; }
}
