package io.zefio.testsupport.payload;

import io.zefio.core.payload.PayloadBuilder;

/**
 * Interface for factory classes that generate test payloads.
 * Provides methods to construct the framework's PayloadBuilder and the corresponding raw byte message.
 */
public interface IPayloadBuilderFactory {
    /**
     * Constructs a configured PayloadBuilder instance.
     * @return Initialized PayloadBuilder
     * @throws Exception If configuration fails
     */
    PayloadBuilder buildEventBuilder() throws Exception;

    /**
     * Generates a raw byte array representing a physical message (e.g., Fixed, Delimiter).
     * @return Physical message bytes
     */
    byte[] buildMessage();
}
