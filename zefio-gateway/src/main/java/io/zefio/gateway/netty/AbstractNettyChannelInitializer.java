package io.zefio.gateway.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.zefio.gateway.netty.dto.NettyValues;

import java.util.concurrent.TimeUnit;

/**
 * Common initializer for Netty channels.
 * Injects baseline security and performance handlers before protocol-specific logic.
 */
public abstract class AbstractNettyChannelInitializer extends ChannelInitializer<NioSocketChannel> {
    private final NettyValues values;
    private final boolean keepAlive;

    public AbstractNettyChannelInitializer(NettyValues values, boolean keepAlive) {
        this.values = values;
        this.keepAlive = keepAlive;
    }

    @Override
    protected void initChannel(NioSocketChannel channel) throws Exception {
        ChannelPipeline pipeline = channel.pipeline();

        // Defense-in-Depth: Terminate idle connections if pooling/keep-alive is not used.
        // This prevents resource exhaustion from "zombie" connections.
        if (!keepAlive && values.getReadTimeout() > 0) {
            pipeline.addFirst("ReadTimeoutHandler",
                    new ReadTimeoutHandler(values.getReadTimeout(), TimeUnit.MILLISECONDS));
        }

        // Delegate to specific implementations (TCP, HTTP, etc.) to add codecs and business handlers
        afterInitPipeLine(pipeline);
    }

    protected abstract void afterInitPipeLine(ChannelPipeline pipeline);
}
