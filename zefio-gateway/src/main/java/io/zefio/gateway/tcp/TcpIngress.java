package io.zefio.gateway.tcp;

import com.google.common.collect.Lists;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.IngressHandler;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.gateway.netty.BaseNettyIngress;
import io.zefio.gateway.netty.dto.ServerHandlerContext;
import io.zefio.gateway.netty.encoder.DefaultByteArrayEncoder;
import io.zefio.gateway.netty.transaction.ITxnManager;
import io.zefio.gateway.netty.util.HandlerFactory;
import io.zefio.gateway.netty.util.NettyUtils;
import io.zefio.gateway.tcp.base.TcpChannelInitializer;
import io.zefio.gateway.tcp.base.TelegramDecoderFactory;
import io.zefio.gateway.tcp.dto.TcpIngressValues;
import io.zefio.gateway.tcp.handler.TcpServerHandler;

/**
 * TCP Server Ingress endpoint implementation.
 */
public class TcpIngress extends BaseNettyIngress {
	protected final TcpIngressValues values;
	protected ITxnManager<Payload> txnManager;

	public TcpIngress(PluginContext context) {
		super(context);
		this.values = yamlMapper.convertValue(context.getContext(), TcpIngressValues.class);
	}

	@Override
	public String getDescription() { return "TCP Server Ingress Endpoint"; }

	@Override
	public void initialise() throws Exception {
		super.initialise();

		// Initialize the transaction manager for request-response correlation
		this.txnManager = NettyUtils.createCallback(
				ingressBuilder,
				this.getPluginName(),
				this.transactionTimeoutMillis,
				isTwoWay(),
				false,
				values.getIngress().getResponseMatchingType());

		// Prepare the factory for dynamic handler instantiation
		handlerFactory = new HandlerFactory(Lists.newArrayList(values.getHandlers()), TcpServerHandler.class);

		if(this.port == -1) {
			throw new FlowException(FlowResultStatus.INTERNAL_SERVER_ERROR, "TCP Ingress port configuration is missing.");
		}
	}

	@Override
	public ChannelInitializer<NioSocketChannel> createHandlerSet(IngressHandler ingressHandler) {
		// Servers use isPooling=false to avoid persistent idle session hanging
		return new TcpChannelInitializer(values.getIngress(), false) {

			@Override
			protected void afterInitPipeLine(ChannelPipeline pipeline) {
				// 1. Register specialized telegram decoders (Length, Delimiter, etc.)
				TelegramDecoderFactory.addTelegramDecoders(flowName, pipeline, ingress, values);

				// 2. Instantiate and register business handlers via the factory
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

				// 3. Register the final common byte array encoder
				pipeline.addLast(new DefaultByteArrayEncoder());
			}
		};
	}

	@Override
	public void close() {
		this.txnManager.clear();
		super.close();
	}
}
