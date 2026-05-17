package io.zefio.gateway.websocket;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.zefio.core.IngressHandler;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.gateway.netty.BaseNettyIngress;
import io.zefio.gateway.netty.dto.ServerHandlerContext;
import io.zefio.gateway.netty.transaction.ITxnManager;
import io.zefio.gateway.netty.util.HandlerFactory;
import io.zefio.gateway.netty.util.NettyUtils;
import io.zefio.gateway.websocket.base.WebSocketChannelInitializer;
import io.zefio.gateway.websocket.dto.WebSocketIngressValues;
import io.zefio.gateway.websocket.encoder.PayloadToWebSocketFrameEncoder;
import io.zefio.gateway.websocket.handler.WebSocketServerHandler;
import org.apache.commons.lang3.ObjectUtils;

/**
 * WebSocket Server Ingress Endpoint.
 * Manages the HTTP upgrade handshake and subsequent bidirectional message flow.
 */
public class WebSocketIngress extends BaseNettyIngress {
	protected ITxnManager<Payload> txnManager;
	private final WebSocketIngressValues values;

	public WebSocketIngress(PluginContext context) {
		super(context);
		this.values = yamlMapper.convertValue(context.getContext(), WebSocketIngressValues.class);
	}

	@Override
	public String getDescription() { return "WebSocket Server Ingress Endpoint"; }

	@Override
	public void initialise() throws Exception {
		super.initialise();

		this.txnManager = NettyUtils.createCallback(
				ingressBuilder,
				this.getPluginName(),
				this.transactionTimeoutMillis,
				isTwoWay(),
				false,
				values.getIngress().getResponseMatchingType());

		handlerFactory = new HandlerFactory(values.getHandlers(), WebSocketServerHandler.class);
		if (this.port == 0 || ObjectUtils.isEmpty(values.getContextPath())) {
			throw new FlowException(FlowResultStatus.INTERNAL_SERVER_ERROR, "WebSocket Ingress port or context path is missing.");
		}
	}

	@Override
	public ChannelInitializer<NioSocketChannel> createHandlerSet(IngressHandler ingressHandler) {
		return new WebSocketChannelInitializer(values.getIngress(), false) {

			@Override
			protected void afterInitPipeLine(ChannelPipeline pipeline) {
				// Register standard HTTP codecs required for the initial upgrade handshake
				pipeline.addLast(new HttpServerCodec());
				pipeline.addLast(new HttpObjectAggregator(values.getIngress().getMaxContentLength()));

				// Orchestrates the WebSocket protocol handshake and frame aggregation
				pipeline.addLast(new WebSocketServerProtocolHandler(
						values.getContextPath(),
						null,
						false,
						values.getIngress().getMaxContentLength()
				));

				// Register business logic handlers defined in the flow configuration
				if (!handlerFactory.getHandlerClasses().isEmpty()) {
					try {
						ServerHandlerContext<Payload> context = new ServerHandlerContext<>(
								flowName, ingress, values, values.getIngress(),
								txnManager, ingressHandler, ingressBuilder
						);

						handlerFactory.createServerHandler(context)
								.forEach(pipeline::addLast);
					} catch (Exception e) {
						throw new FlowException(e, FlowResultStatus.INTERNAL_SERVER_ERROR);
					}
				}

				// Upstream stage: Wraps standard engine events back into WebSocket frames
				pipeline.addLast(new PayloadToWebSocketFrameEncoder());

				// Keep-Alive Logic: Sends server-initiated Ping frames on idle events
				pipeline.addLast(new ChannelDuplexHandler() {
					@Override
					public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
						if (evt instanceof IdleStateEvent) {
							ctx.writeAndFlush(new PingWebSocketFrame());
						} else {
							super.userEventTriggered(ctx, evt);
						}
					}
				});
			}
		};
	}

	@Override
	public void close() {
		this.txnManager.clear();
		super.close();
	}
}
