package io.zefio.gateway.websocket.base;

import io.zefio.gateway.netty.AbstractNettyChannelInitializer;
import io.zefio.gateway.netty.dto.NettyValues;

/**
 * Base channel initializer for WebSocket connections.
 * Inherits common defense logic (e.g., ReadTimeout harvesting) from AbstractNettyChannelInitializer.
 */
public abstract class WebSocketChannelInitializer extends AbstractNettyChannelInitializer {

    public WebSocketChannelInitializer(NettyValues values, boolean keepAlive) {
        super(values, keepAlive);
    }
}
