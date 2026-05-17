package io.zefio.core.schema.dto;

import java.nio.charset.Charset;

/**
 * Interface for components that require explicit response encoding configuration.
 */
public interface ResponseEncodingSupport {
    Charset getResponseEncoding();
}
