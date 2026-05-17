package io.zefio.core.common.util;

import io.netty.util.AttributeKey;

/**
 * Utility class that defines constant keys for application-wide attributes,
 * including Netty AttributeKeys for session context and String constants for dynamic processing hints.
 */
public class ApplicationAttributes {
    public static final AttributeKey<String> CORRELATION_ID = AttributeKey.valueOf("CORRELATION_ID");
    public static final AttributeKey<Boolean> SUPPRESS_STAT_LOG = AttributeKey.valueOf("SUPPRESS_STAT_LOG");
    public static final String MDC_CONTEXT_PROPERTY_KEY = "MDC_CONTEXT_MAP";
    public static final String DYNAMIC_RESPONSE_ENCODING = "DYNAMIC_RESPONSE_ENCODING";
    public static final String DYNAMIC_LENGTH_UPDATE = "DYNAMIC_LENGTH_UPDATE";
}
