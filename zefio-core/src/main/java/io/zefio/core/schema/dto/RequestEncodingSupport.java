package io.zefio.core.schema.dto;

import java.nio.charset.Charset;

/**
 * Interface for components that require explicit request encoding configuration.
 */
public interface RequestEncodingSupport {
    Charset getRequestEncoding();
}
