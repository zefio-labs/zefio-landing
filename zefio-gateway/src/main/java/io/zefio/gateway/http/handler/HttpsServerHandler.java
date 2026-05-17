package io.zefio.gateway.http.handler;

import io.zefio.core.payload.Payload;
import io.zefio.gateway.netty.dto.HandlerDefinition;
import io.zefio.gateway.netty.dto.ServerHandlerContext;

/**
 * Extension of HttpServerHandler designated for HTTPS traffic.
 * SSL termination is handled upstream in the Netty pipeline via SslHandler.
 */
public class HttpsServerHandler extends HttpServerHandler {

	public HttpsServerHandler(ServerHandlerContext<Payload> context, HandlerDefinition handlerDef) {
		super(context, handlerDef);
	}
}
