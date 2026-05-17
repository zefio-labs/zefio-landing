package io.zefio.gateway.http.handler;

import io.zefio.core.payload.Payload;
import io.zefio.gateway.netty.dto.ClientHandlerContext;
import io.zefio.gateway.netty.dto.HandlerDefinition;

/**
 * Netty Upstream Handler for secure HTTPS protocols.
 * Inherits body aggregation and parsing logic from the standard HttpClientHandler.
 */
public class HttpsClientHandler extends HttpClientHandler {

	public HttpsClientHandler(ClientHandlerContext<Payload> context, HandlerDefinition handlerDef) {
		super(context, handlerDef);
	}

}
