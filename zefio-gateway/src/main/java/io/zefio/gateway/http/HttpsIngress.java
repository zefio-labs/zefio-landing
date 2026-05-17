package io.zefio.gateway.http;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.common.util.SSLUtils;
import io.zefio.core.IngressHandler;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.gateway.http.base.HttpChannelInitializer;
import io.zefio.gateway.http.dto.HttpsIngressValues;
import io.zefio.gateway.http.handler.HttpsServerHandler;
import io.zefio.gateway.netty.dto.ServerHandlerContext;
import io.zefio.gateway.netty.util.HandlerFactory;
import org.apache.commons.lang3.ObjectUtils;
import org.javatuples.Pair;
import org.springframework.core.io.ClassPathResource;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * HTTPS Server Ingress Endpoint.
 * Extends the standard HTTP Ingress to incorporate SSL/TLS termination.
 */
public class HttpsIngress extends HttpIngress {
	protected final HttpsIngressValues values;
	protected SslContext sslContext;

	public HttpsIngress(PluginContext context) {
		super(context);
		this.values = yamlMapper.convertValue(context.getContext(), HttpsIngressValues.class);
	}

	@Override
	public String getDescription() { return "HTTPS Server Ingress Endpoint"; }

	@Override
	public void initialise() throws Exception {
		handlerFactory = new HandlerFactory(values.getHandlers(), HttpsServerHandler.class);
		if(this.port == -1) {
			throw new FlowException(FlowResultStatus.INTERNAL_SERVER_ERROR, "HTTP(S) Ingress port is not configured (port = -1)");
		}

		if(values.getSsl().getEnable()) {
			// Key store approach
			if (ObjectUtils.isNotEmpty(values.getSsl().getKeyStoreType())) {
				Pair<KeyManagerFactory, TrustManagerFactory> factories = SSLUtils.buildKeyTrustStore(values.getSsl());
				sslContext = SslContextBuilder.forServer(factories.getValue0())
						.trustManager(factories.getValue1())
						.protocols(values.getSsl().getProtocol())
						.build();
			}
			// PEM approach
			else if (ObjectUtils.isNotEmpty(values.getSsl().getCertFilePath())) {
				ClassPathResource certPath = new ClassPathResource(values.getSsl().getCertFilePath());
				ClassPathResource keyPath = new ClassPathResource(values.getSsl().getPrivateKeyFilePath());

				sslContext = SslContextBuilder.forServer(certPath.getInputStream(), keyPath.getInputStream())
						.protocols(values.getSsl().getProtocol())
						.build();
			}
		}

		super.initialise();
	}

	@Override
	public ChannelInitializer<NioSocketChannel> createHandlerSet(IngressHandler ingressHandler) {
		// Optimization: Pass false (non-pooling mode timeout) to protect server resources
		return new HttpChannelInitializer(values.getIngress(), false) {
			@Override
			protected void afterInitPipeLine(ChannelPipeline pipeline) {
				// SSL handler must be the first in the pipeline for decryption
				pipeline.addLast("ssl", sslContext.newHandler(pipeline.channel().alloc()));
				pipeline.addLast(new HttpServerCodec());

				// 1. Create a list of server handlers via handlerFactory and register them to the pipeline
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
}
