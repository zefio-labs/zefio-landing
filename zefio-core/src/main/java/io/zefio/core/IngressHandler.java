package io.zefio.core;

import io.zefio.core.payload.Payload;

/**
 * A functional bridge between the Ingress edge and the Pipeline logic.
 * Invoked when an external request is successfully received and ready for processing.
 */
@FunctionalInterface
public interface IngressHandler {
	void onPayload(Payload payload);
}
