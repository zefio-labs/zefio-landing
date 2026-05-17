package io.zefio.gateway.websocket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.util.FlowErrorUtils;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.ExchangePattern;
import io.zefio.core.payload.Payload;
import io.zefio.core.util.MDCUtils;
import io.zefio.gateway.netty.BaseNettyUpstream;
import io.zefio.gateway.netty.dto.ClientHandlerContext;
import io.zefio.gateway.netty.transaction.FireAndForgetTxnManager;
import io.zefio.gateway.netty.transaction.ITxnManager;
import io.zefio.gateway.netty.util.HandlerFactory;
import io.zefio.gateway.netty.util.NettyUtils;
import io.zefio.gateway.websocket.base.WebSocketChannelInitializer;
import io.zefio.gateway.websocket.dto.WebSocketUpstreamValues;
import io.zefio.gateway.websocket.encoder.PayloadToWebSocketFrameEncoder;
import io.zefio.gateway.websocket.handler.WebSocketClientHandler;
import org.slf4j.MDC;

import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * WebSocket Upstream Endpoint.
 * Handles client-side handshaking and manages persistent bidirectional communication with target servers.
 */
public class WebSocketUpstream extends BaseNettyUpstream {
    protected ITxnManager<Payload> txnManager;
    protected final WebSocketUpstreamValues values;

    public WebSocketUpstream(PluginContext context) {
        super(context);
        this.values = yamlMapper.convertValue(context.getContext(), WebSocketUpstreamValues.class);
    }

    @Override
    public String getDescription() {
        return isTwoWay() ? "WebSocket Upstream Filter (Two-Way)" : "WebSocket Upstream Filter (One-Way)";
    }

    @Override
    public void initialise() throws Exception {
        super.initialise();

        this.host = values.getHost();
        this.port = values.getPort();

        if (exchangePattern == ExchangePattern.RequestReply) {
            this.txnManager = NettyUtils.createCallback(
                    upstreamBuilder,
                    this.getPluginName(),
                    this.transactionTimeoutMillis,
                    true,
                    true,
                    values.getUpstream().getResponseMatchingType());
        } else {
            this.txnManager = new FireAndForgetTxnManager(this.getPluginName());
        }
        handlerFactory = new HandlerFactory(values.getHandlers(), WebSocketClientHandler.class);
    }

    @Override
    public ChannelInitializer<NioSocketChannel> createHandlerSet() {
        return new WebSocketChannelInitializer(values.getUpstream(), true) {
            @Override
            protected void afterInitPipeLine(ChannelPipeline pipeline) {
                URI uri = URI.create(String.format("ws://%s:%d%s", values.getHost(), values.getPort(), values.getUri()));
                WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                        uri, WebSocketVersion.V13, null, false, null
                );

                pipeline.addLast(new HttpClientCodec());
                pipeline.addLast(new HttpObjectAggregator(values.getUpstream().getMaxContentLength()));

                ClientHandlerContext<Payload> context = new ClientHandlerContext<>(
                        flowName, upstream, values, values.getUpstream(), txnManager, upstreamTelegramName
                );

                // Register custom business handlers using specialized constructor injection
                handlerFactory.createHandlers(handlerClass -> {
                    @SuppressWarnings("unchecked")
                    Constructor<WebSocketClientHandler> constructor =
                            (Constructor<WebSocketClientHandler>) handlerClass.getConstructor(
                                    ClientHandlerContext.class, WebSocketClientHandshaker.class);
                    return constructor.newInstance(context, handshaker);
                }).forEach(pipeline::addLast);

                // Attach frame encoder to wrap upstream events back into WebSocket frames
                pipeline.addLast(new PayloadToWebSocketFrameEncoder());
            }
        };
    }

    @Override
    protected CompletableFuture<Channel> getAsyncNettyClient(Payload payload) {
        return createAndConnectAsyncChannel(values.getPoolConfig(), sharedIoPool);
    }

    /**
     * Executes the actual I/O logic once the physical connection and MDC capture are finalized.
     */
    @Override
    public CompletableFuture<Payload> handleChannelIoAsync(Payload payload, Channel channel, Executor flowExecutor) {
        // [Execution Context: Shared-IO Thread] - Verify handshake state
        WebSocketClientHandler handler = channel.pipeline().get(WebSocketClientHandler.class);

        CompletableFuture<Channel> handshakeFuture = new CompletableFuture<>();
        if (handler.handshakeFuture().isSuccess()) {
            handshakeFuture.complete(channel);
        } else {
            // Register listener for handshake completion (executed by Netty IO thread)
            handler.handshakeFuture().addListener(f -> {
                if (f.isSuccess()) handshakeFuture.complete(channel);
                else handshakeFuture.completeExceptionally(f.cause());
            });
        }

        // Proceed after handshake completion
        return handshakeFuture.thenCompose(ch -> {

            // Restore MDC context for Netty EventLoop thread logging
            MDCUtils.restoreMdc(payload);

            try {
                log.info("WebSocket Request Upstream: channel id[{}] length[{}]", ch.id().asShortText(), payload.getBody().length);

                // [Execution Context: Netty EventLoop] - Data transmission
                return txnManager.send(ch, payload)
                        // [Thread Switch] Netty IO Thread -> Business Worker Thread
                        .handleAsync((responseEvent, ex) -> {

                            // [Execution Context: Business Worker (flowExecutor)]
                            MDCUtils.restoreMdc(payload);

                            try {
                                if (ex != null) throw new CompletionException(FlowErrorUtils.convert(ex));
                                return responseEvent;
                            } catch (Exception e) {
                                FlowException fe = FlowErrorUtils.convert(e);
                                log.warn("WebSocket Response Handling Error: {}", fe.getMessage());
                                throw new CompletionException(fe);
                            } finally {
                                MDC.clear();
                            }
                        }, flowExecutor)

                        .whenComplete((result, ex) -> {
                            cleanupChannel(ch, this.channelManager, values.getPoolConfig(), false);
                        });
            } finally {
                MDC.clear();
            }
        });
    }

    @Override
    public void close() {
        if (this.txnManager != null) {
            this.txnManager.clear();
        }
        super.close();
    }
}
