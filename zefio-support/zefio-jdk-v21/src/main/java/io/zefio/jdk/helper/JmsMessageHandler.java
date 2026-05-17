package io.zefio.jdk.helper;

/**
 * Functional interface for processing messages received from a JMS provider.
 * Serves as the callback handler for Ingress message listeners.
 */
@FunctionalInterface
public interface JmsMessageHandler {
	/**
	 * Handles the received JMS message payload.
	 *
	 * @param message The raw message object (e.g., Map, String, or Bytes).
	 */
	void handle(Object message);
}
