package io.zefio.gateway.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.common.util.CommonUtils;
import io.zefio.core.config.monitor.MonitorProperties.ConnectionPoolThreshold;
import io.zefio.core.config.monitor.MonitorProperties.NettyEventLoopThreshold;
import io.zefio.core.BaseIngress;
import io.zefio.core.Ingress;
import io.zefio.core.IngressHandler;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.telemetry.AbstractMonitorLogger;
import io.zefio.core.telemetry.MonitorInitContext;
import io.zefio.core.telemetry.netty.NettyEventLoopStateTracker;
import io.zefio.core.telemetry.netty.NettyThreadPoolMonitorLogger;
import io.zefio.gateway.netty.dto.NettyValues;
import io.zefio.gateway.netty.util.HandlerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Base class for all Netty-based Ingress implementations.
 * Manages the server lifecycle, socket options, and global channel tracking for monitoring.
 */
public abstract class BaseNettyIngress extends BaseIngress {

	private final NettyValues values;
	protected final Ingress ingress;
	protected HandlerFactory handlerFactory;
	private static final String SUFFIX_NETTY = "-";

	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;
	protected ChannelInitializer<NioSocketChannel> handlerSet;
	protected ServerBootstrap bootstrap;
	protected final Integer port;

	// Track all active channels for telemetry and graceful shutdown orchestration
	protected final ChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

	public BaseNettyIngress(PluginContext context) {
		super(context);
		this.values = yamlMapper.convertValue(context.getContext(), NettyValues.class);
		this.transactionTimeoutMillis = values.getTransactionTimeoutMillis();
		this.port = values.getPort();
		this.ingress = this;
	}

	@Override
	public void initialise() throws Exception {
		super.initialise();

		// BossGroup handles incoming connection attempts
		this.bossGroup = new NioEventLoopGroup(1);

		// WorkerGroup handles the actual I/O traffic for established connections
		this.workerGroup = new NioEventLoopGroup(
				values.getWorkThreadCount(),
				CommonUtils.getThreadFactory(this.flowName + SUFFIX_NETTY + this.pluginName)
		);

		this.bootstrap = new ServerBootstrap();
		this.bootstrap.group(this.bossGroup, this.workerGroup)
				.channel(NioServerSocketChannel.class)
				.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(20480))
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, values.getConnectTimeout())
				.childOption(ChannelOption.SO_KEEPALIVE, values.getSoKeepAlive())
				.childOption(ChannelOption.TCP_NODELAY, values.getTcpNoDelay())
				.childOption(ChannelOption.SO_REUSEADDR, values.getSoReUseAddr());
	}

	/** To be implemented by protocol-specific Ingresses (e.g., TCP, HTTP) */
	public abstract ChannelInitializer<NioSocketChannel> createHandlerSet(IngressHandler ingressHandler);

	@Override
	public void receive(IngressHandler ingressHandler) {
		this.handlerSet = createHandlerSet(ingressHandler);
		this.bootstrap
				.localAddress(new InetSocketAddress(this.port))
				.childHandler(new ChannelInitializer<NioSocketChannel>() {
					@Override
					protected void initChannel(NioSocketChannel ch) throws Exception {
						// 1. Register new connection to the global group for monitoring
						allChannels.add(ch);

						// 2. Delegate to the protocol-specific handler set
						ch.pipeline().addLast(handlerSet);
					}
				});

		log.info("{} Initialized at port [{}]", this.logHeader, this.port);

		ChannelFuture f;
		try {
			// Bind and start to accept incoming connections
			f = this.bootstrap.bind().sync();
			f.channel().closeFuture().sync();
		} catch (Exception e) {
			if (e.getMessage() == null) {
				// Handle clean interruption during system shutdown
				log.debug("[{}] Receive interrupted during shutdown: {}", pluginName, e.getClass().getSimpleName());
			} else {
				// Critical errors (e.g., Port BindException) trigger a fatal alert
				logFatalStartupError(e);
				throw new FlowException(e, FlowResultStatus.INTERNAL_SERVER_ERROR);
			}
		} finally {
			workerGroup.shutdownGracefully();
			bossGroup.shutdownGracefully();
		}
	}

	@Override
	public void close() {
		// Stop accepting new connections immediately
		if (bossGroup != null) {
			bossGroup.shutdownGracefully(100, 3000, TimeUnit.MILLISECONDS).syncUninterruptibly();
		}

		// Allow current I/O tasks to finish before closing workers
		if (workerGroup != null) {
			workerGroup.shutdownGracefully(100, 3000, TimeUnit.MILLISECONDS).syncUninterruptibly();
		}

		super.close();
	}

	/** Configures Netty-specific metrics loggers for the monitoring manager */
	public List<AbstractMonitorLogger> setupAndRegisterNettyMonitor(NettyEventLoopThreshold nettyEventLoopThreshold, ConnectionPoolThreshold connectionPoolThreshold) {
		List<AbstractMonitorLogger> loggers = new ArrayList<>();

		// Tracker provides the current count of active channels
		NettyEventLoopStateTracker nettyTracker = new NettyEventLoopStateTracker(workerGroup, allChannels::size);

		loggers.add(
				new NettyThreadPoolMonitorLogger(
						MonitorInitContext.builder()
								.flowName(flowName)
								.flowLabel(flowLabel)
								.moduleName(pluginName)
								.moduleLabel(pluginLabel)
								.sharedScheduler(sharedScheduledPool)
								.meterRegistry(meterRegistry)
								.build(),
						nettyTracker, nettyEventLoopThreshold)
		);

		return loggers;
	}
}
