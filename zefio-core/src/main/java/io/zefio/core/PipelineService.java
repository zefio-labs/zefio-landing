package io.zefio.core;

import io.zefio.core.payload.Payload;
import io.zefio.core.telemetry.provider.IMetricsResettable;

/**
 * Manages the execution pipeline of a single flow.
 * It handles the initialization of processors, starts the ingress module,
 * and provides the dispatching mechanism that pushes data into the
 * internal processing stages (SEDA queues).
 * Extended with abstraction methods to support zero-downtime hot-swap orchestration.
 */
public interface PipelineService extends IMetricsResettable {
    void initialise() throws Exception;
    void start() throws Exception;
    void resetMetrics();
    boolean shutdown();

    /** Dispatches the payload into the execution pipeline. */
    void dispatch(Payload payload);

    String getName();

    /** Returns the Ingress module associated with this pipeline. */
    Ingress getIngress();

    /** Closes only the inbound listener socket while keeping worker threads alive for draining. */
    void stopListening();

    /** Evaluates whether all asynchronous SEDA worker stages (CPU/IO) are completely empty. */
    boolean isAllQueueEmpty();
}
