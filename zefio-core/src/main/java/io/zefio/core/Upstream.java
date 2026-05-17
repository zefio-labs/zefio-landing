package io.zefio.core;

import io.zefio.core.config.monitor.MonitorProperties.ConnectionPoolThreshold;
import io.zefio.core.config.monitor.MonitorProperties.NettyEventLoopThreshold;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.telemetry.AbstractMonitorLogger;

import java.util.List;

/**
 * Interface for outbound connectors (Upstream targets).
 * It supports both request-only and request-reply patterns and
 * provides specialized hooks for low-level monitoring (e.g., Netty metrics).
 */
public interface Upstream extends IoInterceptor {
    boolean isTwoWay();
    PayloadBuilder getEventBuilder();

    /**
     * Registers and returns monitoring trackers for Netty or Connection Pools.
     * Default returns null if the implementation does not support advanced monitoring.
     */
    default List<AbstractMonitorLogger> setupAndRegisterNettyMonitor(
            NettyEventLoopThreshold nettyEventLoopThreshold,
            ConnectionPoolThreshold connectionPoolThreshold) {
        return null;
    }
}
