package io.zefio.core.payload;

/**
 * A callback listener invoked when a flow's execution is finalized.
 * In a SEDA architecture, this provides the bridge back to the Ingress edge
 * to return a response or acknowledge a message.
 */
public interface ResponseListener {

	/**
	 * Invoked when the pipeline completes successfully.
	 * @param payload The final processed payload to be returned to the client.
	 */
	void success(Payload payload);

	/**
	 * Invoked when the pipeline encounters a terminal error.
	 * @param payload The payload containing error details (Throwable).
	 */
	void error(Payload payload);
}
