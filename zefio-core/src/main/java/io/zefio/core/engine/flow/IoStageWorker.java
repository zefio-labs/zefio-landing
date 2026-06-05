package io.zefio.core.engine.flow;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.engine.processor.Processor;
import io.zefio.core.payload.Payload;
import io.zefio.core.telemetry.provider.IQueueStatusProvider;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A dedicated worker for the IO Stage in the SEDA architecture.
 * It manages asynchronous upstream tasks through a blocking queue and coordinates
 * the handoff between IO and compute stages via a PipelineOrchestrator.
 */
public class IoStageWorker implements IQueueStatusProvider {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private static final String IO_WORKER = "-io-worker";

    private final String flowName;
    private final FlowErrorHandler errorHandler;

    private final BlockingQueue<IoTask> queue;
    private final int queueCapacity;
    private final ExecutorService ioExecutor;

    @Setter
    private PipelineOrchestrator pipelineOrchestrator;
    private volatile boolean isRunning = false;
    private Thread workerThread;

    public IoStageWorker(FlowInitContext ctx, FlowErrorHandler errorHandler) {
        this.flowName = ctx.getFlowName();
        this.errorHandler = errorHandler;

        this.queueCapacity = ctx.getOptions().getIoQueue().getCapacity();
        this.queue = new LinkedBlockingQueue<>(queueCapacity);

        this.ioExecutor = ctx.getSharedPools().getSharedMdcIoExecutor();
    }

    public void start() {
        this.isRunning = true;

        this.workerThread = new Thread(() -> {
            log.info("IoStage Worker thread [{}] started.", Thread.currentThread().getName());

            // Process tasks until the queue is empty even if the worker is stopped
            while (isRunning || !queue.isEmpty()) {
                try {
                    // Apply short-polling to balance responsiveness and CPU usage
                    IoTask task = queue.poll(isRunning ? 100 : 0, TimeUnit.MILLISECONDS);
                    if (task != null) {
                        executeUpstreamAsync(task);
                    } else if (!isRunning) {
                        break;
                    }
                } catch (InterruptedException e) {
                    log.warn("[{}] IoStage Worker forcibly interrupted. Exiting.", flowName);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("[{}] IoStage Worker loop error", flowName, e);
                }
            }
            log.info("IoStage Worker thread [{}] finished.", Thread.currentThread().getName());
        }, flowName + IO_WORKER);
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    /**
     * Enqueues a task for asynchronous IO processing (Handoff from PipelineProcessor)
     */
    public void dispatchAsync(Payload payload, Processor processor, int index) {
        if (!isRunning || !queue.offer(new IoTask(payload, processor, index))) {
            payload.setThrowable(new FlowException(FlowResultStatus.QUEUE_CAPACITY_EXCEEDED, "I/O Queue Full"));
            errorHandler.handleError(payload.getThrowable(), payload, payload.getCallback(), 0, ioExecutor);
        }
    }

    /**
     * Executes actual upstream IO and determines flow control
     */
    private void executeUpstreamAsync(IoTask task) {
        // Retries are managed internally by the Processor
        task.processor.executeAsync(task.payload, ioExecutor)
                .whenCompleteAsync((res, ex) -> {
                    if (ex != null) {
                        errorHandler.handleError(ex, task.payload, task.payload.getCallback(), 1, ioExecutor);
                    } else {
                        // On success, return control to the CPU orchestrator and increment pipeline index
                        pipelineOrchestrator.process(res, task.index + 1);
                    }
                }, ioExecutor);
    }

    private static class IoTask {
        Payload payload;
        Processor processor;
        int index;
        IoTask(Payload e, Processor p, int i) { this.payload = e; this.processor = p; this.index = i; }
    }

    public void stop() {
        this.isRunning = false;
        if (workerThread != null) {
            try {
                // Ensure remaining queue data is processed with a 5-second grace period
                workerThread.join(5000);
            } catch (InterruptedException ignored) {}
        }
    }

    /**
     * Evaluates if the internal IO blocking queue contains no pending operations.
     * Crucial for verifying absolute pipeline exhaustion states during active hot-swaps.
     */
    public boolean isQueueEmpty() { return this.queue.isEmpty(); }
    @Override public int getQueueSize() { return queue.size(); }
    @Override public int getQueueCapacity() { return queueCapacity; }
}
