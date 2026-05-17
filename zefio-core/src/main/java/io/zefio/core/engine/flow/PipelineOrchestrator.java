package io.zefio.core.engine.flow;

import io.zefio.core.engine.processor.Processor;
import io.zefio.core.payload.Payload;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Orchestrates the execution sequence of the pipeline.
 * It manages the transition between processing steps and implements the SEDA (Staged Event-Driven Architecture)
 * handoff by delegating blocking tasks to the IO stage worker while maintaining asynchronous compute flows.
 */
public class PipelineOrchestrator {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final List<Processor> rootPipeline;
    private final ExecutorService flowExecutor;
    private final FlowErrorHandler errorHandler;

    @Setter
    private IoStageWorker ioStageWorker;

    public PipelineOrchestrator(FlowInitContext context, ExecutorService flowExecutor, FlowErrorHandler errorHandler) {
        this.rootPipeline = context.getRootPipeline();
        this.flowExecutor = flowExecutor;
        this.errorHandler = errorHandler;
    }

    /**
     * Entry point for the recursive pipeline execution starting from a specific index.
     */
    public void process(Payload payload, int index) {
        // [Termination Condition] Executed when all processors in the pipeline are finished
        if (index >= rootPipeline.size()) {
            if (payload.getCallback() != null) {
                payload.getCallback().success(payload);
            }
            return;
        }

        Processor processor = rootPipeline.get(index);

        // [SEDA Handoff] If the next processor is a blocking type, transfer control to the IO stage
        if (processor.isBlockingType()) {
            ioStageWorker.dispatchAsync(payload, processor, index);
            // Release the current thread immediately to process other ingress messages
            return;
        }

        // CPU Async Execution via CompletableFuture chaining
        processor.executeAsync(payload, flowExecutor)
                .whenCompleteAsync((res, ex) -> {
                    if (ex != null) {
                        errorHandler.handleError(ex, payload, payload.getCallback(), 1, flowExecutor);
                    } else {
                        // Proceed to the next top-level index upon success
                        process(res, index + 1);
                    }
                }, flowExecutor);
    }
}
