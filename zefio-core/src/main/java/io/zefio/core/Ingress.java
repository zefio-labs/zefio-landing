package io.zefio.core;

import io.zefio.core.config.monitor.MonitorProperties.ConnectionPoolThreshold;
import io.zefio.core.config.monitor.MonitorProperties.NettyEventLoopThreshold;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.telemetry.AbstractMonitorLogger;
import java.util.List;

/**
 * Interface for ingress connectors (e.g., HTTP Server, MQ Listener).
 * Acts as the entry point for incoming requests and delegates them to the pipeline.
 */
public interface Ingress extends GatewayPlugin {
	/** Sets the handler to be invoked when a new payload is received. */
	void receive(IngressHandler ingressHandler);

	boolean isTwoWay();
	PayloadBuilder getEventBuilder();

	/**
	 * Optional hooks for Netty-level monitoring if the ingress is based on Netty.
	 */
	default List<AbstractMonitorLogger> setupAndRegisterNettyMonitor(
			NettyEventLoopThreshold nettyEventLoopThreshold,
			ConnectionPoolThreshold connectionPoolThreshold) {
		return null;
	}
}
