package io.zefio.gateway.tcp.base;

import io.zefio.gateway.netty.AbstractNettyChannelInitializer;
import io.zefio.gateway.netty.dto.NettyValues;

/**
 * Provides the base structure for TCP channel pipeline initialization.
 * Inherits common defense logic such as ReadTimeout from AbstractNettyChannelInitializer.
 */
public abstract class TcpChannelInitializer extends AbstractNettyChannelInitializer {

    public TcpChannelInitializer(NettyValues values, boolean keepAlive) {
        super(values, keepAlive);
    }
}
