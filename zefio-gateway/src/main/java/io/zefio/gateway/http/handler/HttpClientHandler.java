package io.zefio.gateway.http.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.common.util.ApplicationAttributes;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ZefioMessage;
import io.zefio.gateway.http.util.HttpUtils;
import io.zefio.gateway.netty.dto.ClientHandlerContext;
import io.zefio.gateway.netty.dto.HandlerDefinition;
import io.zefio.gateway.netty.handler.AbstractUpstreamHandler;
import io.zefio.gateway.netty.util.NettyUtils;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Netty Upstream Handler for HTTP protocols.
 * Aggregates chunked HTTP responses and transforms them into domain Payload objects
 * before completing the asynchronous transaction.
 */
public class HttpClientHandler extends AbstractUpstreamHandler<HttpObject, Payload> {

    // Utilizes a persistent buffer per channel to prevent memory fragmentation
    protected ByteBuf aggregatedContent;
    protected HttpResponse response;

    public HttpClientHandler(ClientHandlerContext<Payload> context, HandlerDefinition handlerDef) {
        super(context);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        // Allocate a dedicated buffer using the channel's allocator when the handler is attached
        this.aggregatedContent = ctx.alloc().buffer();
    }

    @Override
    protected void handleRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {

        // Handle the initial HttpResponse line and headers
        if (msg instanceof HttpResponse) {
            this.response = (HttpResponse) msg;
            NettyUtils.runWithMdc(ctx.channel(), () -> log.debug("HTTP Response Status received: {}", response.status()));
        }

        // Handle body chunks
        if (msg instanceof HttpContent) {
            HttpContent httpContent = (HttpContent) msg;
            ByteBuf chunk = httpContent.content();

            // Prevent processing if the buffer has already been cleared due to previous errors
            if (aggregatedContent == null) return;

            // [Security] Validate accumulated length to prevent OutOfMemory attacks
            int totalLen = aggregatedContent.readableBytes() + chunk.readableBytes();
            if (totalLen > maxContentLength) {
                log.error("Response body exceeded maxContentLength limit: {} > {}", totalLen, maxContentLength);
                handleExceededLength(ctx, totalLen); // Parent triggers ctx.close()
                releaseAggregatedContent(); // Nullify and release immediately
                return;
            }

            aggregatedContent.writeBytes(chunk);

            // Handle the final chunk signaling the end of the HTTP transmission
            if (msg instanceof LastHttpContent) {
                FullHttpResponse fullHttpResponse = null;
                try {
                    // Assemble the full response using a retained duplicate of the accumulated content
                    fullHttpResponse = buildFullHttpResponse(response, aggregatedContent);

                    // Promote the standard HTTP response into a Zefio Payload event
                    Payload responsePayload = parseHttpResponseToPayload(fullHttpResponse);

                    // Forward the finalized event to the Transaction Manager to complete the pending promise
                    this.txnManager.complete(ctx.channel(), responsePayload);

                    NettyUtils.runWithMdc(ctx.channel(), () -> log.debug("HTTP Response Aggregated: status={}, length={}", response.status(), aggregatedContent.readableBytes()) );

                } catch (FlowException fe) {
                    // Handle edge cases where the transaction may have already timed out or been closed.
                    // Logging only to prevent disruptive pipeline exceptions.
                    log.warn("Transaction already handled or channel closed: {}", fe.getMessage());

                } finally {
                    // Critical memory management: Release the assembled response if it wasn't consumed
                    if (fullHttpResponse != null && fullHttpResponse.refCnt() > 0) {
                        fullHttpResponse.release();
                    }

                    // Clear the pooled buffer for the next transaction on this persistent channel
                    if (aggregatedContent != null && aggregatedContent.refCnt() > 0) {
                        aggregatedContent.clear();
                    }
                    this.response = null;
                }
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        releaseAggregatedContent();
        super.exceptionCaught(ctx, cause);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        releaseAggregatedContent();
        super.handlerRemoved(ctx);
    }

    private void releaseAggregatedContent() {
        if (aggregatedContent != null) {
            ReferenceCountUtil.safeRelease(aggregatedContent);
            aggregatedContent = null;
        }
    }

    private FullHttpResponse buildFullHttpResponse(HttpResponse response, ByteBuf content) {
        FullHttpResponse fullResponse = new DefaultFullHttpResponse(
                response.protocolVersion(),
                response.status(),
                content.retainedDuplicate() // Generate a lightweight copy that shares the same underlying memory
        );

        // Copy the original headers into the unified response object
        fullResponse.headers().set(response.headers());

        boolean keepAlive = HttpUtil.isKeepAlive(fullResponse);

        if (keepAlive) {
            fullResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
            fullResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        } else {
            fullResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        }

        return fullResponse;
    }

    /**
     * Dismantles the FullHttpResponse and maps its metadata and body into the internal Payload model.
     */
    private Payload parseHttpResponseToPayload(FullHttpResponse response) throws Exception {
        // Resolve character encoding dynamically from the response headers, or fallback to configuration
        Charset charset = HttpUtils.resolveCharsetFromContentType(response.headers(), responseEncoding);
        if (charset == null) charset = responseEncoding;

        // Extract raw bytes (Used for normal body processing as well as error payload attachment)
        byte[] body = new byte[response.content().readableBytes()];
        response.content().readBytes(body);

        // Instantiate the empty Payload object with the raw bytes and resolved charset
        Payload payload = new ZefioMessage(body, charset);
        payload.setTelegramName(this.context.getTelegramName());

        // Escape Hatch: Inject the dynamically resolved encoding back into the event headers
        if (charset != null) {
            payload.setHeader(ApplicationAttributes.DYNAMIC_RESPONSE_ENCODING, charset.name());
        }

        // Map HTTP Headers to Event Properties
        Map<String, Object> headerMap = new HashMap<>();
        response.headers().forEach(e -> headerMap.put(e.getKey().toLowerCase(), e.getValue()));
        payload.setHeader(headerMap);

        // Advanced Content Type Handling (Multipart / File Attachments)
        String contentType = response.headers().get(HttpHeaderNames.CONTENT_TYPE);
        String contentDisposition = response.headers().get(HttpHeaderNames.CONTENT_DISPOSITION);

        // Handle Multipart Form Responses
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
            String boundary = HttpUtils.extractBoundary(contentType);
            HttpUtils.parseMultipartResponse(response.content(), boundary, payload);
        }
        // Handle Single File Attachments (e.g., application/octet-stream)
        else if (contentDisposition != null && contentDisposition.toLowerCase().contains(HttpHeaderValues.ATTACHMENT.toString())) {
            HttpUtils.processSingleFileResponse(response.content(), contentDisposition, payload);
        }

        // Note: Standard bodies (JSON, XML, TEXT) are already loaded into the Payload via the constructor.

        // Error Handling: Map non-200 responses to domain exceptions while preserving the error body
        int statusCode = response.status().code();
        if (statusCode != HttpResponseStatus.OK.code()) {
            FlowResultStatus status = HttpUtils.fromHttpStatusCode(statusCode);
            org.springframework.http.HttpHeaders httpHeaders = new org.springframework.http.HttpHeaders();
            response.headers().forEach(entry -> httpHeaders.add(entry.getKey(), entry.getValue()));

            // Convert body bytes to string for JSON error extraction and logging
            String errorBodyString = new String(body, charset);
            log.warn("Upstream Server Error [{}]: {}", statusCode, errorBodyString);

            // Construct the root cause exception including the raw bytes
            RestClientResponseException cause = new RestClientResponseException(
                    "HTTP " + statusCode + " " + response.status().reasonPhrase(),
                    statusCode, response.status().reasonPhrase(), httpHeaders, body, charset
            );

            // Inject the exception along with the status code and error body into the payload
            payload.setThrowable(new FlowException(cause, status, String.valueOf(statusCode), errorBodyString));
        }

        // Lightweight logging focused on HTTP metadata
        log.info("HTTP Response Ingress: statusCode=[{}] dynamicEncoding=[{}]", response.status(), charset);

        if (log.isDebugEnabled()) {
            log.debug("HTTP Response Headers: {}", response.headers().entries());
        }

        return payload;
    }
}
