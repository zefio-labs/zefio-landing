package io.zefio.gateway.http;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.IngressHandler;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.gateway.http.dto.HttpIngressValues;
import io.zefio.gateway.http.handler.HttpServerHandler;
import io.zefio.gateway.netty.BaseNettyIngress;
import io.zefio.gateway.http.base.HttpChannelInitializer;
import io.zefio.gateway.netty.dto.ServerHandlerContext;
import io.zefio.gateway.netty.transaction.ITxnManager;
import io.zefio.gateway.netty.util.HandlerFactory;
import io.zefio.gateway.netty.util.NettyUtils;

/**
 * Netty-based HTTP Ingress component.
 * Responsible for bootstrapping the server socket, managing the Netty pipeline,
 * and delegating incoming HTTP requests to the Zefio Flow engine.
 */
public class HttpIngress extends BaseNettyIngress {

	protected ITxnManager<Payload> txnManager;
	protected final HttpIngressValues values;

	public HttpIngress(PluginContext context) {
		super(context);
		this.values = yamlMapper.convertValue(context.getContext(), HttpIngressValues.class);
	}

	@Override
	public String getDescription() {
		return "HTTP Server Ingress endpoint";
	}

	@Override
	public void initialise() throws Exception {
		super.initialise();

		// Establish the transaction manager to handle asynchronous request-reply correlations
		this.txnManager = NettyUtils.createCallback(
				ingressBuilder,
				this.getPluginName(),
				this.transactionTimeoutMillis,
				isTwoWay(),
				false,
				values.getIngress().getResponseMatchingType());

		handlerFactory = new HandlerFactory(values.getHandlers(), HttpServerHandler.class);

		if(this.port == -1) {
			throw new FlowException(FlowResultStatus.INTERNAL_SERVER_ERROR, "HTTP(S) Ingress port is not configured (port = -1)");
		}
	}

	@Override
	public ChannelInitializer<NioSocketChannel> createHandlerSet(IngressHandler ingressHandler) {
		// Enforce strict connection harvesting: The server will not tolerate idle connections indefinitely.
		// `keepAlive` is set to false here to trigger the ReadTimeoutHandler inherited from the base class.
		return new HttpChannelInitializer(values.getIngress(), false) {
			@Override
			protected void afterInitPipeLine(ChannelPipeline pipeline) {

				// Add standard Netty HTTP Codec (Combination of HttpRequestDecoder and HttpResponseEncoder)
				pipeline.addLast(new HttpServerCodec());

				// Note: HttpObjectAggregator is intentionally omitted here to support streaming
				// and chunked transfer natively in downstream handlers.

				// Generate and append the custom server handlers defined via the handlerFactory
				if (!handlerFactory.getHandlerClasses().isEmpty()) {
					try {
						ServerHandlerContext<Payload> context = new ServerHandlerContext<>(
								flowName, ingress, values, values.getIngress(), txnManager, ingressHandler, ingressBuilder
						);
						handlerFactory.createServerHandler(context).forEach(pipeline::addLast);
					} catch (Exception e) {
						throw new FlowException(e, FlowResultStatus.INTERNAL_SERVER_ERROR);
					}
				}
			}
		};
	}

	@Override
	public void close() {
		// Fail-fast all pending promises before shutting down the underlying Netty transport
		this.txnManager.clear();
		super.close();
	}
}
