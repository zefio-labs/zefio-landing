package io.zefio.core;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.payload.Payload;

/**
 * A specialized interceptor for CPU-bound computations.
 * Adds a synchronous 'process' method for logic that doesn't involve external I/O.
 */
public interface ComputeInterceptor extends GatewayInterceptor {
    /** Performs synchronous data manipulation or validation. */
    Payload process(Payload payload) throws FlowException;
}
