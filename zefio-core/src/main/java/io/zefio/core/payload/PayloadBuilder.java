package io.zefio.core.payload;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.payload.builder.config.Telegram;

import java.nio.charset.Charset;
import java.util.Map;

/**
 * Interface for building and transforming message payloads.
 * It handles the conversion between raw objects and byte arrays,
 * manages correlation ID extraction, and supports lazy parsing for SpEL evaluation.
 */
public interface PayloadBuilder {

	/**
	 * Constructs a Payload object from the original raw data.
	 */
	Payload withBody(Object original, Charset encoding) throws FlowException;

	/**
	 * Extracts a transaction identifier from the body based on the telegram configuration.
	 */
	String extractCorrelationId(Object original, Object obj, Charset encoding) throws FlowException;

	/**
	 * Performs final byte-level adjustments (e.g., length headers, padding)
	 * before the payload is sent to an Upstream system.
	 */
	Payload finalizeUpstreamPayload(Payload payload, Charset target) throws FlowException;

	/**
	 * Returns the metadata definition (Telegram) associated with this builder.
	 */
	Telegram getTelegram();

	/**
	 * Converts raw bytes into a Map structure for SpEL-based navigation.
	 * This is the core of the Lazy Parsing mechanism.
	 */
	Map<String, Object> parseToMap(byte[] body, Charset encoding) throws Exception;

	/**
	 * Serializes a modified Map back into the physical byte format.
	 * Used for Write-Back operations where the payload content is updated during the flow.
	 */
	byte[] buildFromMap(Map<String, Object> map, Charset encoding) throws Exception;
}
