package io.zefio.gateway.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.common.util.SSLUtils;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.spel.PayloadExpressionEvaluator;
import io.zefio.gateway.http.base.HttpChannelInitializer;
import io.zefio.gateway.http.dto.HttpsUpstreamValues;
import io.zefio.gateway.http.handler.HttpsClientHandler;
import io.zefio.gateway.http.util.HttpUtils;
import io.zefio.gateway.netty.dto.ClientHandlerContext;
import io.zefio.gateway.netty.util.HandlerFactory;
import org.apache.commons.lang3.ObjectUtils;
import org.javatuples.Pair;
import org.springframework.core.io.ClassPathResource;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.util.List;

/**
 * HTTPS Upstream Endpoint.
 * Extends standard HTTP Upstream to support secure client connections.
 */
public class HttpsUpstream extends HttpUpstream {
	protected final HttpsUpstreamValues values;
	private SslContext sslContext;

	public HttpsUpstream(PluginContext context) {
		super(context);
		this.values = yamlMapper.convertValue(context.getContext(), HttpsUpstreamValues.class);
	}

	@Override
	public String getDescription() { return "HTTPS Upstream Filter"; }

	@Override
	public void initialise() throws Exception {
		handlerFactory = new HandlerFactory(values.getHandlers(), HttpsClientHandler.class);
		useHttps = true;

		if(values.getSsl().getEnable()) {
			// Key store approach
			if( ObjectUtils.isNotEmpty(values.getSsl().getKeyStoreType()) ) {
				Pair<KeyManagerFactory, TrustManagerFactory> factories = SSLUtils.buildKeyTrustStore(values.getSsl());
				sslContext = SslContextBuilder.forClient()
						.keyManager(factories.getValue0())
						.trustManager(factories.getValue1())
						.protocols(values.getSsl().getProtocol())
						.build();
			}
			// PEM approach
			else if( ObjectUtils.isNotEmpty(values.getSsl().getCertFilePath()) ) {
				ClassPathResource certPath = new ClassPathResource(values.getSsl().getCertFilePath());
				//       ClassPathResource keyPath = new ClassPathResource(httpsOption.getSslPrivateKeyFilePath());

				sslContext = SslContextBuilder.forClient()
						.trustManager(certPath.getInputStream())  // Input server certificate or CA certificate
						//             .keyManager(clientCert, clientKey)        // Input client certificate and key for mutual TLS
						.protocols(values.getSsl().getProtocol())
						.build();
			}
		}

		super.initialise();
	}

	@Override
	public ChannelInitializer<NioSocketChannel> createHandlerSet(){
		return new HttpChannelInitializer(values.getUpstream(), values.getUpstream().getKeepAlive()) {

			@Override
			protected void afterInitPipeLine(ChannelPipeline pipeline) {
				// 1. SSL handler must be placed at the very front (encryption/decryption is the highest priority)
				if (sslContext != null) {
					pipeline.addFirst("ssl", new SslHandler(sslContext.newEngine(pipeline.channel().alloc())));
				}
				pipeline.addLast(new HttpClientCodec());
				pipeline.addLast(new HttpObjectAggregator(values.getUpstream().getMaxContentLength()));

				ClientHandlerContext<Payload> context = new ClientHandlerContext<>(
						flowName, upstream, values, values.getUpstream(), txnManager, upstreamTelegramName
				);

				// 2. Create a list of client handlers via handlerFactory and register them to the pipeline
				if (handlerFactory != null && !handlerFactory.getHandlerClasses().isEmpty()) {
					try {
						handlerFactory.createClientHandler(context).forEach(pipeline::addLast);
					} catch (Exception e) {
						throw new FlowException(e, FlowResultStatus.INTERNAL_SERVER_ERROR);
					}
				}

				// Attach Payload -> FullHttpRequest conversion encoder, same as HttpUpstream
				pipeline.addLast(new MessageToMessageEncoder<Payload>() {
					@Override
					protected void encode(ChannelHandlerContext ctx, Payload msg, List<Object> out) throws Exception {
						// 1. Obtain raw URL defined in YAML (e.g., /base/test/#{body.TARGET_VAL})
						String rawUrl = HttpUtils.resolveUrl(values, msg, useHttps);

						// 2. Assemble dynamic path via SpEL evaluator
						String evaluatedUrl = PayloadExpressionEvaluator.evaluateString(rawUrl, msg);

						FullHttpRequest request = buildRequest(ctx.channel(), msg, evaluatedUrl);
						out.add(request);
					}
				});
			}
		};
	}
}
