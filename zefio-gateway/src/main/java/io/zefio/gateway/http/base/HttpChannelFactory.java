package io.zefio.gateway.http.base;

import io.netty.bootstrap.Bootstrap;
import io.zefio.gateway.netty.client.AbstractNettyChannelFactory;
import io.zefio.gateway.netty.dto.PoolConfig;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Concrete factory for HTTP Netty Channels.
 * Manages the creation, validation, and destruction of persistent HTTP Upstream connections.
 */
public class HttpChannelFactory extends AbstractNettyChannelFactory {

    public HttpChannelFactory(Bootstrap b, String h, int p, long t, PoolConfig pc, AtomicBoolean r) {
        super(b, h, p, t, pc, r);
    }

    /**
     * Returns the protocol name for logging and telemetry identification.
     */
    @Override
    protected String getProtocolName() {
        return "HTTP";
    }
}
