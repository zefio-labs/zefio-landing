package io.zefio.core.telemetry.netty;

/**
 * Provider interface for retrieving the status of a connection pool.
 * Typically used for Upstream connectors to monitor resource availability and usage.
 */
public interface IConnectionPoolStatusProvider {

    /** Returns the number of connections currently borrowed and in use. */
    int getActiveConnections();

    /** Returns the number of idle connections waiting in the pool. */
    int getIdleConnections();

    /** Returns the maximum number of connections allowed in the pool. */
    int getMaxConnections();

    /** Returns the number of threads or requests waiting for a connection. */
    int getWaitQueueSize();
}
