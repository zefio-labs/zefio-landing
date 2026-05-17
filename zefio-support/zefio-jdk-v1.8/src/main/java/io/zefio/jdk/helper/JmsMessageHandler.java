package io.zefio.jdk.helper;

/**
 * Functional interface for handling messages received from a JMS provider.
 * Acts as a bridge between the Ingress listener and the internal processing logic.
 */
@FunctionalInterface
public interface JmsMessageHandler {
	/**
	 * Processes the raw message payload.
	 * @param message The received message object.
	 */
	void handle(Object message);
}
