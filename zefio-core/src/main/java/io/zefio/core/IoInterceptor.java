package io.zefio.core;

/**
 * A specialized interceptor focused on I/O operations.
 * Allows the engine to distinguish between blocking and non-blocking
 * modules to optimize thread pool assignment.
 */
public interface IoInterceptor extends GatewayInterceptor {
    /**
     * Defaults to true (Blocking).
     * Implementations should override this to false for non-blocking I/O.
     */
    default boolean isBlockingType() {
        return true;
    }
}
