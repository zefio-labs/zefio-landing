package io.zefio.core;

import io.zefio.core.payload.Payload;
import io.zefio.core.telemetry.provider.IMetricsResettable;

/**
 * Manages the execution pipeline of a single flow.
 * It handles the initialization of processors, starts the ingress module,
 * and provides the dispatching mechanism that pushes data into the
 * internal processing stages (SEDA queues).
 */
public interface PipelineService extends IMetricsResettable {
    void initialise() throws Exception;
    void start() throws Exception;
    void resetMetrics();
    boolean shutdown();

    /** Dispatches the payload into the execution pipeline. */
    void dispatch(Payload payload);

    /** Returns the Ingress module associated with this pipeline. */
    Ingress getIngress();
}
