package io.zefio.gateway.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.util.CommonUtils;
import io.zefio.core.common.util.FlowErrorUtils;
import io.zefio.core.payload.Payload;
import io.zefio.core.config.monitor.MonitorProperties.ConnectionPoolThreshold;
import io.zefio.core.config.monitor.MonitorProperties.NettyEventLoopThreshold;
import io.zefio.core.BaseUpstream;
import io.zefio.core.Upstream;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.util.MDCUtils;
import io.zefio.core.telemetry.AbstractMonitorLogger;
import io.zefio.core.telemetry.MonitorInitContext;
import io.zefio.core.telemetry.netty.ConnectionPoolMonitorLogger;
import io.zefio.core.telemetry.netty.NettyEventLoopStateTracker;
import io.zefio.core.telemetry.netty.NettyThreadPoolMonitorLogger;
import io.zefio.gateway.netty.client.NettyChannelManager;
import io.zefio.gateway.netty.client.NettyClientHelper;
import io.zefio.gateway.netty.dto.NettyValues;
import io.zefio.gateway.netty.dto.PoolConfig;
import io.zefio.gateway.netty.util.HandlerFactory;
import io.zefio.gateway.netty.util.NettyUtils;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for all Netty-based Upstream implementations.
 * Manages the lifecycle of the Bootstrap, EventLoopGroup, and provides the backbone
 * for asynchronous I/O and MDC traceability.
 */
public abstract class BaseNettyUpstream extends BaseUpstream {

    private final NettyValues values;
    protected final Upstream upstream;
    protected HandlerFactory handlerFactory;
    private static final String SUFFIX_NETTY = "-";

    protected NettyChannelManager channelManager;
    protected final AtomicBoolean running = new AtomicBoolean(true);

    protected Bootstrap bootstrap;
    private EventLoopGroup workerGroup;
    protected ChannelInitializer<NioSocketChannel> handlerSet;

    protected String host;
    protected int port;

    public BaseNettyUpstream(PluginContext context) {
        super(context);
        this.values = yamlMapper.convertValue(context.getContext(), NettyValues.class);
        this.transactionTimeoutMillis = values.getTransactionTimeoutMillis();
        this.upstream = this;
    }

    @Override
    public Payload blockingProcessInternal(Payload payload) throws FlowException {
        throw new UnsupportedOperationException("Netty Upstream does not support blocking processing.");
    }

    /** Acquire the Netty Channel asynchronously (to be implemented by protocol logic). */
    protected abstract CompletableFuture<Channel> getAsyncNettyClient(Payload payload);

    /** Perform the actual I/O logic once the channel is obtained. */
    protected abstract CompletableFuture<Payload> handleChannelIoAsync(Payload payload, Channel channel, Executor flowExecutor);

    /**
     * Common logic to acquire a channel while preserving MDC context.
     * Prevents log loss in decoders by capturing MDC before returning the channel.
     */
    protected CompletableFuture<Channel> acquireChannelWithMdc(Payload payload, NettyChannelManager manager, PoolConfig pool, Executor ioPool) {
        if (pool.getPoolMaxSize() == 0) {
            // [Non-Pooling Mode]
            return createAndConnectAsyncChannel(pool, ioPool)
                    .thenApply(ch -> {
                        // Restore MDC to the connecting thread to capture attributes
                        MDCUtils.restoreMdc(payload);
                        try {
                            NettyUtils.captureMdc(ch);
                            return ch;
                        } finally {
                            MDC.clear();
                        }
                    });
        } else {
            // [Pooling Mode]
            CompletableFuture<Channel> future = new CompletableFuture<>();

            // Thread Switch: Worker -> Shared-IO
            // Offload the borrowChannel task to avoid stalling the business thread during pool contention.
            ioPool.execute(() -> {
                MDCUtils.restoreMdc(payload);
                try {
                    // Borrow channel (uses non-blocking/maxWait logic defined in the manager)
                    Channel ch = manager.borrowChannel();
                    NettyUtils.captureMdc(ch);

                    // Future is completed by the 'Shared-IO' thread
                    future.complete(ch);
                } catch (Exception e) {
                    future.completeExceptionally(FlowErrorUtils.convert(e));
                } finally {
                    MDC.clear();
                }
            });

            return future;
        }
    }

    /**
     * Common cleanup logic to return or close a channel.
     */
    protected void cleanupChannel(Channel channel, NettyChannelManager manager, PoolConfig poolConfig, boolean isKeepAlive) {
        if (channel == null) return;

        if (poolConfig != null && poolConfig.getPoolMaxSize() > 0 && isKeepAlive) {
            if (manager != null) {
                // Delegate the return task to the shared pool to avoid I/O thread blocking
                sharedIoPool.submit(() -> manager.returnChannel(channel));
            }
        } else {
            channel.close();
            log.debug("Channel closed (Non-pooling or Keep-Alive disabled)");
        }
    }

    /**
     * Orchestrates the high-level Netty async flow.
     */
    @Override
    protected CompletableFuture<Payload> handleIoAsync(Payload payload, Executor flowExecutor) {
        // 1. Acquire Channel: Completed by 'Shared-IO' thread
        return getAsyncNettyClient(payload)

                // 2. Transmit Data: Thread remains 'Shared-IO'
                // Delegated immediately to handleChannelIoAsync which hands off to 'Netty EventLoop'.
                .thenCompose(channel -> {
                    MDCUtils.restoreMdc(payload);
                    try {
                        return handleChannelIoAsync(payload, channel, flowExecutor);
                    } finally {
                        MDC.clear();
                    }
                })

                // 3. Post-Processing: Thread is 'Worker' (flowExecutor)
                // The protocol implementation ensures the context returns to the Worker pool.
                .whenComplete((result, ex) -> {
                    Payload targetPayload = (result != null) ? result : payload;
                    if (targetPayload != null) {
                        MDCUtils.restoreMdc(targetPayload);
                    }

                    try {
                        if (ex != null) {
                            FlowException finalException = FlowErrorUtils.convert(ex);
                            log.warn("Upstream process failed: {}", finalException.getMessage());
                        }
                    } finally {
                        MDC.clear();
                    }
                });
    }

    public abstract ChannelInitializer<NioSocketChannel> createHandlerSet();

    @Override
    public void initialise() throws Exception {
        super.initialise();
        this.handlerSet = createHandlerSet();

        this.workerGroup = new NioEventLoopGroup(
                values.getWorkThreadCount(),
                CommonUtils.getThreadFactory(this.flowName + SUFFIX_NETTY + this.pluginName)
        );
        this.bootstrap = new Bootstrap();
        this.bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, values.getConnectTimeout())
                .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(2048000))
                .option(ChannelOption.SO_KEEPALIVE, values.getSoKeepAlive())
                .option(ChannelOption.TCP_NODELAY, values.getTcpNoDelay())
                .option(ChannelOption.SO_REUSEADDR, values.getSoReUseAddr());
        this.bootstrap.handler(handlerSet);
    }

    @Override
    public void close() {
        this.running.set(false);

        if (channelManager != null) {
            this.channelManager.shutdown();
        }
        if (workerGroup != null) {
            // Outbound connections close quickly. 100ms quiet period, 3s max timeout.
            workerGroup.shutdownGracefully(100, 3000, TimeUnit.MILLISECONDS).syncUninterruptibly();
        }
        super.close();
    }

    protected CompletableFuture<Channel> createAndConnectAsyncChannel(PoolConfig poolConfig, Executor sharedIoPool) {
        NettyClientHelper client = new NettyClientHelper(bootstrap, host, port);
        return client.connectOnceWithRetry(poolConfig, sharedIoPool)
                .exceptionally(ex -> {
                    throw new CompletionException(FlowErrorUtils.convert(ex));
                });
    }

    /** Registers Netty-specific monitoring loggers for telemetry. */
    public List<AbstractMonitorLogger> setupAndRegisterNettyMonitor(NettyEventLoopThreshold nettyEventLoopThreshold, ConnectionPoolThreshold connectionPoolThreshold) {
        List<AbstractMonitorLogger> loggers = new ArrayList<>();

        NettyEventLoopStateTracker nettyTracker = new NettyEventLoopStateTracker(workerGroup, () -> {
            return (channelManager != null) ? channelManager.getActiveConnections() : 0;
        });

        MonitorInitContext.MonitorInitContextBuilder monitorInitContextBuilder = MonitorInitContext.builder()
                .flowName(flowName)
                .flowLabel(flowLabel)
                .moduleName(pluginName)
                .moduleLabel(pluginLabel)
                .sharedScheduler(sharedScheduledPool)
                .meterRegistry(meterRegistry);

        loggers.add(new NettyThreadPoolMonitorLogger(monitorInitContextBuilder.build(), nettyTracker, nettyEventLoopThreshold));

        if (this.channelManager != null) {
            loggers.add(new ConnectionPoolMonitorLogger(monitorInitContextBuilder.build(), this.channelManager, connectionPoolThreshold));
        }

        return loggers;
    }
}
