package io.zefio.gateway.netty;

import io.netty.util.AttributeKey;
import java.util.Map;

/**
 * Constants for Netty-specific metadata management.
 */
public class NettyMdcConstants {
    /**
     * Key used to store the MDC Map<String, String> context within a Netty Channel attribute.
     */
    public static final AttributeKey<Map<String, String>> MDC_CONTEXT_KEY =
            AttributeKey.valueOf("MDC_CONTEXT");
}
