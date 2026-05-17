package io.zefio.gateway.http.base;

import io.zefio.gateway.netty.AbstractNettyChannelInitializer;
import io.zefio.gateway.netty.dto.NettyValues;

/**
 * Provides the base structure for HTTP channel pipeline initialization.
 * Inherits common defense logic (e.g., ReadTimeout) from AbstractNettyChannelInitializer.
 */
public abstract class HttpChannelInitializer extends AbstractNettyChannelInitializer {

    public HttpChannelInitializer(NettyValues values, boolean keepAlive) {
        super(values, keepAlive);
    }

    // Delegates the abstract afterInitPipeLine method to child classes (e.g., HttpIngress).
    // Can be overridden in the future if common HTTP codecs need to be injected at this base level.
}
