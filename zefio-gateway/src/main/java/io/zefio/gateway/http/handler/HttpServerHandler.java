package io.zefio.gateway.http.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.util.ReferenceCountUtil;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.common.util.CustomMultipartFile;
import io.zefio.core.common.util.FlowErrorUtils;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.PayloadHeaders;
import io.zefio.core.payload.ResponseListener;
import io.zefio.core.payload.builder.config.JsonValues;
import io.zefio.core.payload.builder.config.TelegramValues;
import io.zefio.core.payload.builder.config.XmlValues;
import io.zefio.gateway.http.dto.HttpIngressValues;
import io.zefio.gateway.http.util.HttpUtils;
import io.zefio.gateway.netty.NettyRequestReplyCallback;
import io.zefio.gateway.netty.dto.HandlerDefinition;
import io.zefio.gateway.netty.dto.ServerHandlerContext;
import io.zefio.gateway.netty.handler.AbstractIngressHandler;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;

/**
 * Terminal Ingress handler for HTTP Server endpoints.
 * Manages HTTP payload accumulation, multipart file uploads, and edge security validation.
 */
public class HttpServerHandler extends AbstractIngressHandler<HttpObject> {
    protected ByteBuf partialBody;
    protected HttpPostRequestDecoder decoder;
    protected final MediaType responseContentType;
    protected HttpRequest request;
    protected boolean errorOccurred = false;

    public HttpServerHandler(ServerHandlerContext<Payload> context, HandlerDefinition handlerDef) {
        super(context);
        this.responseContentType = ((HttpIngressValues) context.getValues()).getResponseContentType();
    }

    /**
     * Because HTTP data arrives in chunks, the standard extraction method is not used directly.
     */
    @Override
    protected byte[] handleDataExtraction(ChannelHandlerContext ctx, HttpObject msg) throws FlowException {
        throw new UnsupportedOperationException("HTTP Server Handler uses direct body accumulation.");
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        cleanup();
        super.handlerRemoved(ctx);
    }

    private void cleanup() {
        if (partialBody != null && partialBody.refCnt() > 0) {
            partialBody.release();
            partialBody = null;
        }
        if (decoder != null) {
            decoder.cleanFiles();
            decoder.destroy();
            decoder = null;
        }
    }

    /**
     * Invoked sequentially for each incoming HTTP message chunk.
     */
    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
        if (errorOccurred) {
            ReferenceCountUtil.release(msg);
            return;
        }

        try {
            if (msg instanceof HttpRequest) {
                handleHttpRequest(ctx, (HttpRequest) msg);
            } else if (msg instanceof HttpContent) {
                HttpContent content = (HttpContent) msg;

                // 1. Accumulate incoming chunks
                accumulate(content);

                // 2. Trigger business logic execution when the final chunk arrives
                if (content instanceof LastHttpContent) {
                    if (decoder != null) processMultipartData(ctx);
                    else processNormalBody(ctx);
                }
            }
        } catch (Exception e) {
            this.errorOccurred = true;
            cleanup();
            // Send an error response immediately and close the socket before entering the pipeline
            sendEdgeErrorResponse(ctx, e);
        }
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest req) throws FlowException {
        log.debug(req.toString());

        if (log.isDebugEnabled()) {
            log.debug("HTTP Request Ingress Headers: {}", req.headers().entries());
        }

        // =====================================================================
        // Ingress Edge Validation (Security and Protocol Specification Validation)
        // =====================================================================
        HttpHeaders headers = req.headers();

        // 1. [HTTP Smuggling Defense] Reject if both Transfer-Encoding and Content-Length exist.
        // This violates RFC 7230 and is a common attack vector for request smuggling.
        if (headers.contains(HttpHeaderNames.TRANSFER_ENCODING) && headers.contains(HttpHeaderNames.CONTENT_LENGTH)) {
            throw new FlowException(FlowResultStatus.INGRESS_EDGE_REJECT_PROTOCOL_VIOLATION, "Conflict: Transfer-Encoding and Content-Length cannot coexist");
        }

        // 2. Check for multiple conflicting Content-Length headers.
        List<String> contentLengths = headers.getAll(HttpHeaderNames.CONTENT_LENGTH);
        if (contentLengths.size() > 1 && contentLengths.stream().distinct().count() > 1) {
            throw new FlowException(FlowResultStatus.BAD_REQUEST, "Multiple conflicting Content-Length headers");
        }

        // 3. Check for body presence in GET/HEAD requests.
        HttpMethod method = req.method();
        if ((method == HttpMethod.GET || method == HttpMethod.HEAD) &&
                (headers.contains(HttpHeaderNames.CONTENT_LENGTH) || headers.contains(HttpHeaderNames.TRANSFER_ENCODING))) {
            throw new FlowException(FlowResultStatus.BAD_REQUEST, "GET/HEAD requests should not contain a body");
        }
        // =====================================================================

        long contentLength = HttpUtil.getContentLength(req, -1L);
        if (contentLength > maxContentLength) {
            throw new FlowException(FlowResultStatus.INGRESS_EDGE_REJECT_PAYLOAD_TOO_LARGE, "Payload too large");
        }

        this.request = req;
        cleanup();

        requestEncoding = HttpUtils.resolveCharsetFromContentType(request.headers(), requestEncoding);

        if (HttpPostRequestDecoder.isMultipart(req)) {
            DefaultHttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);
            // Enforce max limit on the factory to control internal temporary file generation
            factory.setMaxLimit(maxContentLength);
            this.decoder = new HttpPostRequestDecoder(factory, request);
        } else {
            this.partialBody = ctx.alloc().buffer();
        }
    }

    private void accumulate(HttpContent content) throws FlowException {
        if (decoder != null) {
            decoder.offer(content);
        } else if (partialBody != null) {
            ByteBuf data = content.content();
            if (data.isReadable()) {
                if (partialBody.readableBytes() + data.readableBytes() > maxContentLength) {
                    throw new FlowException(FlowResultStatus.BAD_REQUEST, "Payload limit exceeded");
                }
                partialBody.writeBytes(data);
            }
        }
    }

    private void processNormalBody(ChannelHandlerContext ctx) throws FlowException {
        try {
            long contentLength = HttpUtil.getContentLength(request, -1L);
            if (contentLength != -1 && partialBody.readableBytes() != contentLength) {
                throw new FlowException(FlowResultStatus.BAD_REQUEST, "Content-Length mismatch");
            }

            byte[] datas = new byte[partialBody.readableBytes()];
            partialBody.readBytes(datas);

            Payload payload = this.ingress.getEventBuilder().withBody(datas, requestEncoding);
            setMdcContext(ctx, payload);
            log.info("Received from channel[{}] id[{}] data[{}]", ctx.channel(), ctx.channel().id(), new String(datas, requestEncoding));

            executeFlow(ctx, payload);
        } finally {
            cleanup();
            MDC.clear();
        }
    }

    private void processMultipartData(ChannelHandlerContext ctx) throws FlowException, IOException {
        try {
            List<MultipartFile> multipartFiles = new ArrayList<>();
            for (InterfaceHttpData data : decoder.getBodyHttpDatas()) {
                if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.FileUpload) {
                    FileUpload upload = (FileUpload) data;
                    if (upload.isCompleted()) {
                        multipartFiles.add(new CustomMultipartFile(upload.get(), upload.getName(), upload.getFilename(), upload.getContentType()));
                    }
                }
            }

            Payload payload = this.ingress.getEventBuilder().withBody(new byte[0], requestEncoding);
            setMdcContext(ctx, payload);

            log.info("Received multipart from channel[{}] id[{}] files[{}]", ctx.channel(), ctx.channel().hashCode(), multipartFiles.size());

            // Store the client-requested files in the standard framework header
            payload.setHeader(PayloadHeaders.HTTP_REQUEST_MULTIPART, multipartFiles);
            executeFlow(ctx, payload);
        } finally {
            cleanup();
            MDC.clear();
        }
    }

    private void executeFlow(ChannelHandlerContext ctx, Payload payload) {
        // 1. Inject HTTP request path (URI)
        String purePath = request.uri();
        try {
            purePath = new URI(request.uri()).getPath();
        } catch (Exception e) {
            log.warn("Invalid URI format: {}", request.uri());
        }
        payload.setHeader(PayloadHeaders.HTTP_REQUEST_PATH, purePath);

        // 2. Inject HTTP method
        payload.setHeader(PayloadHeaders.HTTP_REQUEST_METHOD, request.method().name());

        // 3. Inject existing headers mapped to standard internal prefixes
        request.headers().forEach(e ->
                payload.setHeader(PayloadHeaders.HTTP_REQUEST_PREFIX + e.getKey().toLowerCase(), e.getValue())
        );

        if (log.isDebugEnabled()) {
            log.debug("[Ingress Header Mapping] TID: {} | Mapped Headers: {}",
                    payload.getTrxID(),
                    payload.getSubHeaders(PayloadHeaders.HTTP_REQUEST_PREFIX));
        }

        if (this.ingress.isTwoWay()) {
            payload.setCallback(createResponseListener(payload, ctx));
            sendToIngress(payload, ctx);
        } else {
            payload.setCallback(onewayListener);
            ingressHandler.onPayload(payload);
        }
    }

    @Override
    protected ResponseListener createResponseListener(Payload payload, ChannelHandlerContext ctx) {
        return new NettyRequestReplyCallback(ingress.getMetricsAggregator(), ingress.getEventBuilder(), responseEncoding, ctx) {
            @Override
            public Payload response(Payload payload) {
                try {
                    FullHttpResponse httpResponse = buildHttpResponse(ctx, payload);

                    // Map HTTP Status if the payload passed through the engine error filter
                    if (payload.hasException()) {
                        httpResponse.setStatus(HttpUtils.toHttpResponseStatus(FlowErrorUtils.convert(payload.getThrowable()).getStatus()));
                    }

                    boolean keepAlive = HttpUtil.isKeepAlive(request);
                    if (keepAlive) {
                        httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, httpResponse.content().readableBytes());
                        httpResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                    } else {
                        httpResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
                    }
                    log.debug(httpResponse.toString());

                    if (log.isDebugEnabled()) {
                        log.debug("HTTP Response Upstream Headers: {}", httpResponse.headers().entries());
                    }

                    lastCompleteAndSend(payload, ctx, keepAlive, httpResponse);
                } catch (Exception e) {
                    log.error("Failed to build HTTP response", e);
                    ctx.close();

                }
                return payload;
            }
        };
    }

    private FullHttpResponse buildHttpResponse(ChannelHandlerContext ctx, Payload payload) throws FlowException {
        String ct = responseContentType.toString();
        FullHttpResponse httpResponse = null;

        if ("multipart/form-data".equalsIgnoreCase(ct)) {
            // Extract response files to be sent to the client
            @SuppressWarnings("unchecked")
            List<MultipartFile> files = (List<MultipartFile>) payload.getHeader(PayloadHeaders.HTTP_RESPONSE_MULTIPART);
            String boundary = HttpUtils.generateBoundary();
            ByteBuf buffer = ctx.alloc().buffer();
            try {
                if (files != null) {
                    for (MultipartFile file : files) {
                        HttpUtils.addMultipartFile(buffer, boundary, file);
                    }
                }
                HttpUtils.addMultipartEndBoundary(buffer, boundary);
                httpResponse = new DefaultFullHttpResponse(request.protocolVersion(), OK, buffer);
                httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.MULTIPART_FORM_DATA + "; " + HttpHeaderValues.BOUNDARY + "=" + boundary);
            } catch (Exception e) {
                if (httpResponse != null) {
                    ReferenceCountUtil.safeRelease(httpResponse);
                } else if (buffer != null) {
                    ReferenceCountUtil.safeRelease(buffer);
                }
                throw new FlowException(e, FlowResultStatus.INTERNAL_SERVER_ERROR);
            }
        } else if (HttpHeaderValues.APPLICATION_OCTET_STREAM.toString().equalsIgnoreCase(ct)) {
            // Use RESPONSE_MULTIPART for single file response extraction as well
            @SuppressWarnings("unchecked")
            List<MultipartFile> files = (List<MultipartFile>) payload.getHeader(PayloadHeaders.HTTP_RESPONSE_MULTIPART);

            // Throw an exception immediately if no file or filename is found
            if (files == null || files.isEmpty() || files.get(0).getOriginalFilename() == null) {
                throw new FlowException(FlowResultStatus.INTERNAL_SERVER_ERROR, "No file found to download in response");
            }

            httpResponse = new DefaultFullHttpResponse(request.protocolVersion(), OK, Unpooled.wrappedBuffer(payload.getBody()));
            httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM.toString());
            httpResponse.headers().set(HttpHeaderNames.CONTENT_DISPOSITION,
                    HttpHeaderValues.ATTACHMENT.toString() + "; " + HttpHeaderValues.FILENAME.toString() + "=\"" + files.get(0).getOriginalFilename() + "\"");

        } else {
            // Default JSON / TEXT / XML processing
            httpResponse = new DefaultFullHttpResponse(request.protocolVersion(), OK, Unpooled.copiedBuffer(payload.getBody()));
            httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE,
                    String.format("%s/%s; charset=%s",
                            responseContentType.getType(),
                            responseContentType.getSubtype(),
                            payload.getCurrentEncoding()));
        }

        FullHttpResponse finalHttpResponse = httpResponse;
        payload.getSubHeaders(PayloadHeaders.HTTP_RESPONSE_PREFIX).forEach((k, v) -> finalHttpResponse.headers().set(k, v.toString()));
        return httpResponse;
    }

    /**
     * Transmits the error payload received from the parent according to the HTTP protocol standard.
     */
    @Override
    protected void onSendEdgeError(ChannelHandlerContext ctx, byte[] errorBytes, FlowException flowEx) {
        // 1. HTTP Status mapping (e.g., 400, 500)
        HttpResponseStatus status = HttpUtils.toHttpResponseStatus(flowEx.getStatus());

        // 2. Create FullHttpResponse
        FullHttpResponse errorResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.wrappedBuffer(errorBytes)
        );

        // 3. Determine Content-Type based on Ingress configuration
        TelegramValues telegramValues = this.ingress.getEventBuilder().getTelegram().getValues();
        String contentType = "text/plain";
        if (telegramValues instanceof JsonValues) contentType = "application/json";
        else if (telegramValues instanceof XmlValues) contentType = "application/xml";

        // 4. Configure headers, send, and close channel
        errorResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType + "; charset=" + (requestEncoding != null ? requestEncoding.name() : StandardCharsets.UTF_8.name()));
        errorResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, errorResponse.content().readableBytes());
        errorResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);

        if (log.isDebugEnabled()) {
            log.debug("HTTP Edge Error Response Headers: {}", errorResponse.headers().entries());
        }

        ctx.writeAndFlush(errorResponse).addListener(io.netty.channel.ChannelFutureListener.CLOSE);
    }
}
