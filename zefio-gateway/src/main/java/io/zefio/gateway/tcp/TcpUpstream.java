package io.zefio.gateway.tcp;

import com.google.common.collect.Lists;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.common.util.FlowErrorUtils;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.util.MDCUtils;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ExchangePattern;
import io.zefio.gateway.netty.BaseNettyUpstream;
import io.zefio.gateway.netty.client.NettyChannelManager;
import io.zefio.gateway.netty.dto.ClientHandlerContext;
import io.zefio.gateway.netty.encoder.DefaultByteArrayEncoder;
import io.zefio.gateway.netty.transaction.FireAndForgetTxnManager;
import io.zefio.gateway.netty.transaction.ITxnManager;
import io.zefio.gateway.netty.util.HandlerFactory;
import io.zefio.gateway.netty.util.NettyUtils;
import io.zefio.gateway.tcp.base.TcpChannelFactory;
import io.zefio.gateway.tcp.base.TcpChannelInitializer;
import io.zefio.gateway.tcp.base.TelegramDecoderFactory;
import io.zefio.gateway.tcp.dto.TcpUpstreamValues;
import io.zefio.gateway.tcp.handler.TcpClientHandler;
import org.slf4j.MDC;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * TCP Upstream implementation for sending messages to external systems.
 * Supports asynchronous connection pooling, transaction correlation, and automated retries.
 */
public class TcpUpstream extends BaseNettyUpstream {
    protected ITxnManager<Payload> txnManager;
    protected final TcpUpstreamValues values;

    public TcpUpstream(PluginContext context) {
        super(context);
        this.values = yamlMapper.convertValue(context.getContext(), TcpUpstreamValues.class);
    }

    @Override
    public String getDescription() {
        return isTwoWay() ? "TCP Upstream (Request-Reply) Filter" : "TCP Upstream (Fire-and-Forget) Filter";
    }

    @Override
    public void initialise() throws Exception {
        super.initialise();

        this.host = this.values.getHost();
        this.port = this.values.getUpstream().getPort();

        // Branch TxnManager based on the exchange pattern
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

        // Defensive logic: If Keep-Alive is enabled but pool size is 0, force it to 1
        if (values.getUpstream().getKeepAlive() && values.getPoolConfig().getPoolMaxSize() <= 0) {
            log.warn("[{}] keepAlive is true but poolMaxSize is {}. Forcing poolMaxSize to 1 for connection reuse.",
                    this.getPluginName(), values.getPoolConfig().getPoolMaxSize());
            values.getPoolConfig().setPoolMaxSize(1);
        }

        handlerFactory = new HandlerFactory(Lists.newArrayList(values.getHandlers()), TcpClientHandler.class);

        // TCP considers poolMaxSize > 0 as persistent mode (Keep-Alive)
        if (values.getUpstream().getKeepAlive()) {
            log.info("TCP Keep-Alive mode enabled (PoolSize: {})", values.getPoolConfig().getPoolMaxSize());
            TcpChannelFactory factory = new TcpChannelFactory(bootstrap, host, port, Integer.MAX_VALUE, values.getPoolConfig(), running);
            this.channelManager = new NettyChannelManager(factory, values.getPoolConfig(), sharedIoPool, running);
        } else {
            log.info("TCP Transient mode enabled. Connections will close after each transaction.");
            this.channelManager = null;
        }
    }

    @Override
    public ChannelInitializer<NioSocketChannel> createHandlerSet() {
        return new TcpChannelInitializer(values.getUpstream(), values.getUpstream().getKeepAlive()) {

            @Override
            protected void afterInitPipeLine(ChannelPipeline pipeline) {
                // 1. Register specialized telegram decoders (Length/Delimiter based)
                TelegramDecoderFactory.addTelegramDecoders(flowName, pipeline, upstream, values);

                // 2. Dynamically create and register client handlers via factory
                if (handlerFactory != null && !handlerFactory.getHandlerClasses().isEmpty()) {
                    try {
                        ClientHandlerContext<Payload> context = new ClientHandlerContext<>(
                                flowName, upstream, values, values.getUpstream(), txnManager, upstreamTelegramName
                        );
                        handlerFactory.createClientHandler(context)
                                .forEach(pipeline::addLast);
                    } catch (Exception e) {
                        throw new FlowException(e, FlowResultStatus.INTERNAL_SERVER_ERROR);
                    }
                }

                // 3. Register the default common byte array encoder as the last upstream stage
                pipeline.addLast(new DefaultByteArrayEncoder());
            }
        };
    }

    @Override
    protected CompletableFuture<Channel> getAsyncNettyClient(Payload payload) {
        return acquireChannelWithMdc(payload, this.channelManager, values.getPoolConfig(), sharedIoPool);
    }

    /**
     * Executes the actual I/O logic after channel acquisition and MDC capture are complete.
     */
    @Override
    public CompletableFuture<Payload> handleChannelIoAsync(Payload payload, Channel channel, Executor flowExecutor) {
        // [Execution Context: Shared-IO Thread]
        log.info("TCP Request Upstream: channel id[{}] length[{}]", channel.id().asShortText(), payload.getBody().length);

        // 1. Initiate I/O operation (Transmission delegated to Netty EventLoop)
        return txnManager.send(channel, payload)
                // [Thread Switch: Netty IO Thread -> Business Worker Thread]
                .handleAsync((responseEvent, ex) -> {

                    // [Execution Context: Business Worker Thread (flowExecutor)]
                    // Restore MDC context immediately after thread context switch
                    MDCUtils.restoreMdc(payload);

                    try {
                        // 2. Fail-Fast: Propagation of network/timeout errors
                        if (ex != null) throw new CompletionException(FlowErrorUtils.convert(ex));

                        return responseEvent;

                    } catch (Exception e) {
                        FlowException fe = FlowErrorUtils.convert(e);
                        log.warn("TCP Response Handling Error: {}", fe.getMessage());
                        throw new CompletionException(fe);
                    } finally {
                        // 3. Resource Cleanup: Clear MDC context to prevent context leakage
                        MDC.clear();
                    }
                }, flowExecutor)

                // 4. Finalize channel state (Return to pool or close)
                .whenComplete((result, ex) -> {
                    cleanupChannel(channel, this.channelManager, values.getPoolConfig(), values.getUpstream().getKeepAlive());
                });
    }

    @Override
    public void close() {
        // 1. Business-level shutdown (Fail-Fast)
        // Release pending transaction futures to unblock worker threads
        if (this.txnManager != null) {
            this.txnManager.clear();
        }

        // 2. Infrastructure-level shutdown (Blocking)
        // Gracefully shut down physical Netty channels and thread pools
        super.close();
    }
}
