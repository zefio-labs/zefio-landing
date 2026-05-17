package io.zefio.gateway.error.common;

import io.zefio.core.payload.Payload;

import java.nio.charset.Charset;

/**
 * Interface for mutating payload data during error handling.
 * Implementations apply format-specific rules (Fixed, JSON, XML) to inject error codes and messages.
 */
public interface ErrorMessageEditor {
    byte[] edit(Payload payload, Charset encoding, Throwable throwable) throws Exception;
}
