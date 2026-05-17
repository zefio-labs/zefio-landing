package io.zefio.gateway.http;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.common.util.FlowErrorUtils;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.util.MDCUtils;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.PayloadHeaders;
import io.zefio.core.payload.spel.PayloadExpressionEvaluator;
import io.zefio.gateway.http.base.HttpChannelFactory;
import io.zefio.gateway.http.base.HttpChannelInitializer;
import io.zefio.gateway.http.dto.HttpUpstreamValues;
import io.zefio.gateway.http.handler.HttpClientHandler;
import io.zefio.gateway.http.util.HttpUtils;
import io.zefio.gateway.netty.BaseNettyUpstream;
import io.zefio.gateway.netty.client.NettyChannelManager;
import io.zefio.gateway.netty.dto.ClientHandlerContext;
import io.zefio.gateway.netty.transaction.ITxnManager;
import io.zefio.gateway.netty.util.HandlerFactory;
import io.zefio.gateway.netty.util.NettyUtils;
import org.javatuples.Pair;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Netty-based HTTP Upstream component.
 * Responsible for initiating connections to external HTTP servers, dynamically encoding
 * internal Payloads into HTTP requests, and handling the asynchronous response lifecycle.
 */
public class HttpUpstream extends BaseNettyUpstream {
    protected ITxnManager<Payload> txnManager;
    protected final HttpUpstreamValues values;

    protected boolean useHttps = false;

    public HttpUpstream(PluginContext context) {
        super(context);
        this.values = yamlMapper.convertValue(context.getContext(), HttpUpstreamValues.class);
    }

    @Override
    public String getDescription() { return "Filter for transmitting data to external HTTP Upstream servers."; }

    @Override
    public void initialise() throws Exception {
        super.initialise();

        // Establish the transaction manager to handle asynchronous request-reply correlations
        this.txnManager = NettyUtils.createCallback(
                upstreamBuilder,
                this.getPluginName(),
                this.transactionTimeoutMillis,
                isTwoWay(),
                true,
                values.getUpstream().getResponseMatchingType());

        this.host = values.getHost();
        this.port = values.getUpstream().getPort();

        // Architectural Guardrail: Force a minimum pool size if Keep-Alive is enabled
        if (values.getUpstream().getKeepAlive() && values.getPoolConfig().getPoolMaxSize() <= 0) {
            log.warn("[{}] keepAlive is true but poolMaxSize is {}. Forcing poolMaxSize to 1 for connection reuse.",
                    this.getPluginName(), values.getPoolConfig().getPoolMaxSize());
            values.getPoolConfig().setPoolMaxSize(1);
        }

        handlerFactory = new HandlerFactory(values.getHandlers(), HttpClientHandler.class);

        if (values.getUpstream().getKeepAlive()) {
            // Keep-Alive Mode: Initialize the connection pool manager
            log.info("HTTP Keep-Alive mode enabled (PoolSize: {})", values.getPoolConfig().getPoolMaxSize());
            HttpChannelFactory factory = new HttpChannelFactory(bootstrap, host, port, Integer.MAX_VALUE, values.getPoolConfig(), running);
            this.channelManager = new NettyChannelManager(factory, values.getPoolConfig(), sharedIoPool, running);
        } else {
            // Transient Mode: Create a new connection for every transaction
            log.info("HTTP Transient mode enabled. Connection will close after transaction.");
            this.channelManager = null;
        }
    }

    @Override
    public ChannelInitializer<NioSocketChannel> createHandlerSet() {
        return new HttpChannelInitializer(values.getUpstream(), values.getUpstream().getKeepAlive()) {

            @Override
            protected void afterInitPipeLine(ChannelPipeline pipeline) {
                // Add standard Netty HTTP Client Codec
                pipeline.addLast(new HttpClientCodec());
                pipeline.addLast(new HttpObjectAggregator(values.getUpstream().getMaxContentLength()));

                ClientHandlerContext<Payload> context = new ClientHandlerContext<>(
                        flowName, upstream, values, values.getUpstream(), txnManager, upstreamTelegramName
                );

                // Inject custom Upstream handlers generated via the HandlerFactory
                if (handlerFactory != null && !handlerFactory.getHandlerClasses().isEmpty()) {
                    try {
                        handlerFactory.createClientHandler(context).forEach(pipeline::addLast);
                    } catch (Exception e) {
                        throw new FlowException(e, FlowResultStatus.INTERNAL_SERVER_ERROR);
                    }
                }

                // Terminal Encoder: Transforms the high-level Payload into a Netty FullHttpRequest
                // exactly at the moment it needs to be flushed to the wire.
                pipeline.addLast(new MessageToMessageEncoder<Payload>() {
                    @Override
                    protected void encode(ChannelHandlerContext ctx, Payload msg, List<Object> out) throws Exception {
                        // 1. Resolve raw URL from YAML configuration
                        String rawUrl = HttpUtils.resolveUrl(values, msg, useHttps);

                        // 2. Evaluate dynamic URL path segments using SpEL
                        String evaluatedUrl = PayloadExpressionEvaluator.evaluateString(rawUrl, msg);

                        FullHttpRequest request = buildRequest(ctx.channel(), msg, evaluatedUrl);
                        out.add(request);
                    }
                });
            }
        };
    }

    @Override
    protected CompletableFuture<Channel> getAsyncNettyClient(Payload payload) {
        return acquireChannelWithMdc(payload, this.channelManager, values.getPoolConfig(), sharedIoPool);
    }

    /**
     * Executes the HTTP I/O logic after a channel has been successfully acquired.
     * Manages the thread handoff between the Netty EventLoop and the Zefio Flow Executor.
     */
    @Override
    public CompletableFuture<Payload> handleChannelIoAsync(Payload payload, Channel channel, Executor flowExecutor) {

        // Ensure Logging Context (MDC) is present on the I/O thread before sending
        MDCUtils.restoreMdc(payload);

        try {
            log.info("HTTP Request Upstream: TID[{}] length[{}]", payload.getTrxID(), payload.getBody() != null ? payload.getBody().length : 0);

            // Delegate the network write to the TxnManager.
            // The Netty EventLoop handles the flush and immediately returns a Future.
            return txnManager.send(channel, payload)

                    // Context Switch: Transition from Netty EventLoop back to the Business Worker thread
                    .handleAsync((responseEvent, ex) -> {

                        // Restore the transaction MDC context on the worker thread
                        MDCUtils.restoreMdc(payload);

                        try {
                            // Fail-Fast: Propagate any I/O exceptions that occurred during transmission
                            if (ex != null) {
                                throw new CompletionException(FlowErrorUtils.convert(ex));
                            }
                            return responseEvent;

                        } catch (Exception e) {
                            FlowException fe = FlowErrorUtils.convert(e);
                            log.error("HTTP Response Handling Error: {}", fe.getMessage());
                            throw new CompletionException(fe);
                        } finally {
                            // Clean up MDC to prevent context bleeding across worker threads
                            MDC.clear();
                        }
                    }, flowExecutor)

                    // Ensure the physical channel is returned to the pool or closed appropriately
                    .whenComplete((result, ex) -> {
                        cleanupChannel(channel, this.channelManager, values.getPoolConfig(), values.getUpstream().getKeepAlive());
                    });
        } finally {
            MDC.clear();
        }
    }

    /**
     * Constructs the Netty FullHttpRequest based on the payload data, evaluating dynamic
     * SpEL configurations for query parameters and headers.
     */
    protected FullHttpRequest buildRequest(Channel channel, Payload payload, String url) throws FlowException {
        FullHttpRequest request = null;
        ByteBuf content = channel.alloc().buffer();

        try {
            // =====================================================================
            // Dynamic Query Parameter Assembly via SpEL and QueryStringEncoder
            // =====================================================================
            if (values.getRequestQueryParams() != null && !values.getRequestQueryParams().isEmpty()) {

                // Evaluate SpEL expressions mapped in the configuration
                List<Pair<String, String>> evaluatedParams = HttpUtils.getHttpHeaderKeyValueConvertor(
                        values.getRequestQueryParams(), payload, payload.getCurrentEncoding());

                // Utilize Netty's QueryStringEncoder for safe URL encoding
                QueryStringEncoder encoder =
                        new io.netty.handler.codec.http.QueryStringEncoder(url, payload.getCurrentEncoding());

                for (Pair<String, String> param : evaluatedParams) {
                    encoder.addParam(param.getValue0(), param.getValue1());
                }

                url = encoder.toString();

                if (log.isDebugEnabled()) {
                    log.debug("HTTP Request Upstream Final URL with QueryParams: [{}]", url);
                }
            }
            // =====================================================================

            // Evaluate Content-Type to determine framing strategy
            MediaType reqType = values.getRequestContentType();
            boolean isMultipart = reqType != null && "multipart".equalsIgnoreCase(reqType.getType());

            if (isMultipart) {
                String boundary = HttpUtils.generateBoundary();

                // Check for explicit MultipartFile attachments in the Payload headers
                if (payload.containsKeyHeader(PayloadHeaders.HTTP_REQUEST_MULTIPART)) {
                    Object multipartObj = payload.getHeader(PayloadHeaders.HTTP_REQUEST_MULTIPART);

                    if (multipartObj instanceof MultipartFile) {
                        MultipartFile file = (MultipartFile) multipartObj;
                        HttpUtils.addMultipartFile(content, boundary, file);
                        log.info("Request attachment form-field[{}] fileName[{}]", file.getName(), file.getOriginalFilename());
                    }
                    else if (multipartObj instanceof List<?>) {
                        @SuppressWarnings("unchecked")
                        List<MultipartFile> fileList = (List<MultipartFile>) multipartObj;
                        for (Object obj : fileList) {
                            if (obj instanceof MultipartFile) {
                                MultipartFile file = (MultipartFile) obj;
                                HttpUtils.addMultipartFile(content, boundary, file);
                                log.info("Request attachment form-field[{}] fileName[{}]", file.getName(), file.getOriginalFilename());
                            }
                        }
                    }
                }
                // Fallback: Transmit raw bytes if no explicit MultipartFiles are found
                else if (payload.getBody() != null && payload.getBody().length > 0) {
                    content.writeBytes(payload.getBody());
                    log.info("Request raw multipart data transmitted directly from payload body");
                }
                else {
                    content.writeBytes(new byte[0]);
                    log.warn("Empty multipart request generated");
                }

                // Append the closing boundary
                HttpUtils.addMultipartEndBoundary(content, boundary);

                request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(values.getRequestHttpMethod().name()), url, content);
                prepareRequestHttpHeader(request, payload);

                // Explicitly set the Multipart boundary header
                request.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.MULTIPART_FORM_DATA + "; " + HttpHeaderValues.BOUNDARY + "=" + boundary);
            }
            // Standard Request Handling (JSON, XML, Plain Text, etc.)
            else {
                if (payload.getBody() != null) {
                    content.writeBytes(payload.getBody());
                }
                request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(values.getRequestHttpMethod().name()), url, content);
                prepareRequestHttpHeader(request, payload);

                request.headers().set(HttpHeaderNames.CONTENT_TYPE, reqType + "; charset=" + payload.getCurrentEncoding().name());
            }

            log.info("HTTP Request Upstream: url=[{}] method=[{}] encoding=[{}]", request.uri(), request.method(), payload.getCurrentEncoding());

            if (log.isDebugEnabled()) {
                log.debug("HTTP Request Headers: {}", request.headers().entries());
            }
            return request;
        } catch (Exception e) {
            // Guard against memory leaks (Double-Free prevention)
            if (request != null && request.refCnt() > 0) {
                ReferenceCountUtil.release(request);
            }
            else if (content != null && content.refCnt() > 0) {
                ReferenceCountUtil.release(content);
            }
            log.warn("Failed to build FullHttpRequest for TID: {}, [{}]", payload.getTrxID(), e.getMessage());
            throw new FlowException(e, FlowResultStatus.MESSAGE_FORMAT_ERROR);
        }
    }

    private void prepareRequestHttpHeader(FullHttpRequest request, Payload payload) throws FlowException {
        // Define a blacklist of reserved headers to prevent accidental overriding
        List<String> restrictedHeaders = Arrays.asList(
                HttpHeaderNames.CONTENT_LENGTH.toString(),
                HttpHeaderNames.TRANSFER_ENCODING.toString(),
                HttpHeaderNames.HOST.toString(),
                HttpHeaderNames.CONNECTION.toString(),
                HttpHeaderNames.CONTENT_TYPE.toString(),
                PayloadHeaders.HTTP_REQUEST_PATH.replace(PayloadHeaders.HTTP_REQUEST_PREFIX, ""),
                PayloadHeaders.HTTP_REQUEST_METHOD.replace(PayloadHeaders.HTTP_REQUEST_PREFIX, "")
        );

        // 1. Inject dynamically mapped headers accumulated from upstream filters
        Map<String, Object> httpRequestHeaderProperty = payload.getSubHeaders(PayloadHeaders.HTTP_REQUEST_PREFIX);
        for (Map.Entry<String, Object> entry : httpRequestHeaderProperty.entrySet()) {
            String originalKey = entry.getKey();
            String lowerKey = originalKey.toLowerCase();

            // Preserve original casing for valid custom headers
            if (!restrictedHeaders.contains(lowerKey)) {
                request.headers().set(originalKey, entry.getValue().toString());
            }
        }

        // 2. Inject explicit headers defined in the YAML configuration
        List<Pair<String, String>> headerList = HttpUtils.getHttpHeaderKeyValueConvertor(values.getRequestHeaderKeyValues(), payload, payload.getCurrentEncoding());
        for(Pair<String, String> pair : headerList) {
            request.headers().set(pair.getValue0(), pair.getValue1());
        }

        // 3. Apply standard protocol headers
        request.headers().set(HttpHeaderNames.HOST, this.host + ":" + this.port);
        request.headers().set(HttpHeaderNames.CONNECTION, values.getUpstream().getKeepAlive() ? HttpHeaderValues.KEEP_ALIVE : HttpHeaderValues.CLOSE);
        request.headers().set(HttpHeaderNames.ACCEPT, values.getRequestAccept());
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes());
    }

    @Override
    public void close() {
        // Fast-Fail: Immediately resolve all pending promises to unblock worker threads
        if (this.txnManager != null) {
            this.txnManager.clear();
        }

        // Gracefully shut down the underlying Netty transport
        super.close();
    }
}
