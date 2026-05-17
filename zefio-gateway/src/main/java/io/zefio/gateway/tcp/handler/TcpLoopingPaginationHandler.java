package io.zefio.gateway.tcp.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.zefio.gateway.netty.chunked.dto.ChunkPaginationConfig;
import io.zefio.gateway.netty.chunked.dto.PaginationRequestStrategy;
import io.zefio.gateway.netty.chunked.dto.PaginationResponseStrategy;
import io.zefio.gateway.netty.dto.HandlerDefinition;
import io.zefio.gateway.netty.handler.AbstractCustomHandler;

import java.nio.charset.StandardCharsets;

/**
 * TCP Business Pagination and Data Merging Handler.
 * Logic Flow:
 * 1. Checks a specific flag or condition in the response to determine if more pages exist.
 * 2. If pagination is required, it resends the modified request to the Upstream server.
 * 3. Extracts the body segment from every response and merges them into a single buffer.
 * 4. Once all pages are collected, it transmits the combined header and merged bodies as a single message.
 */
public class TcpLoopingPaginationHandler extends AbstractCustomHandler<ByteBuf> {
    private byte[] lastRequest;
    private ByteBuf mergedBodyBuffer;
    private byte[] firstHeader;
    private int currentLoopCount = 0;

    public TcpLoopingPaginationHandler(Object context, HandlerDefinition handlerDef) {
        super(context, handlerDef);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        // Step 1: Capture the request message for subsequent re-requests during pagination
        if (msg instanceof ByteBuf) {
            this.lastRequest = ByteBufUtil.getBytes((ByteBuf) msg);
        } else if (msg instanceof byte[]) {
            this.lastRequest = (byte[]) msg;
        }

        // Pass the message to the next handler in the pipeline
        super.write(ctx, msg, promise);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        String channelId = ctx.channel().id().asShortText();

        // Debug Log: Inspect the raw data received from the Upstream server
        byte[] rawResponse = ByteBufUtil.getBytes(msg);
        log.info(">>> [{}] RAW_RECEIVE (Len: {}): {}",
                channelId, rawResponse.length, new String(rawResponse, StandardCharsets.UTF_8).replace(" ", "_"));

        // Bypass if no pagination configuration is provided
        ChunkPaginationConfig config = handlerDef.getPagination();
        if (config == null) {
            ctx.fireChannelRead(ReferenceCountUtil.retain(msg));
            return;
        }

        byte[] response = ByteBufUtil.getBytes(msg);
        int bodyStart = config.getBodyOffset() != null ? config.getBodyOffset() : 120;

        // Phase 1: Accumulate data and preserve the initial header
        if (firstHeader == null) {
            // Store the header from the very first response to use in the final merged message
            firstHeader = new byte[bodyStart];
            System.arraycopy(response, 0, firstHeader, 0, bodyStart);

            // Allocate initial buffer for body merging
            mergedBodyBuffer = ctx.alloc().buffer(response.length * 3);
        }

        int bodyLen = response.length - bodyStart;
        if (bodyLen > 0) {
            mergedBodyBuffer.writeBytes(response, bodyStart, bodyLen);
            log.debug("[{}] Accumulated: {} bytes, Total: {}", channelId, bodyLen, mergedBodyBuffer.readableBytes());
        }

        // Phase 2: Determine if the loop should continue based on the Response Strategy
        if (shouldContinue(config, response, bodyLen)) {
            currentLoopCount++;

            // Prevent infinite loops by enforcing a maximum page limit
            if (currentLoopCount >= config.getMaxPages()) {
                log.warn("[{}] Reached max pagination limit: {}", channelId, config.getMaxPages());
                finalizeAndFire(ctx);
                return;
            }

            // Phase 3: Process the request based on the Request Strategy and resend
            try {
                byte[] nextRequest = prepareNextRequest(config, response);
                log.info("[{}] Pagination matched. Resending next request (Strategy: {})", channelId, config.getRequestStrategy());
                ctx.writeAndFlush(Unpooled.copiedBuffer(nextRequest));
            } catch (IndexOutOfBoundsException e) {
                // Handle cases where the response is too short to extract keys; force stop the loop
                log.error("[{}] Invalid response length for pagination. Force stopping loop.", channelId);
                finalizeAndFire(ctx);
            }
        } else {
            // Phase 4: Pagination complete; deliver the merged data to the next handler
            finalizeAndFire(ctx);
        }
    }

    /**
     * Determines whether to continue the pagination loop based on the response.
     */
    private boolean shouldContinue(ChunkPaginationConfig config, byte[] response, int bodyLen) {
        PaginationResponseStrategy strategy = config.getResponseStrategy() != null ?
                config.getResponseStrategy() : PaginationResponseStrategy.FLAG_MATCH;

        switch (strategy) {
            case BODY_NOT_EMPTY:
                return bodyLen > 0;
            case MAX_COUNT:
                return true; // Continue until MaxPages is reached
            case FLAG_MATCH:
            default:
                // Check if a specific status field matches the 'continue' value (e.g., '1')
                int offset = config.getStatusOffset() != null ? config.getStatusOffset() : 14;
                String contVal = config.getLoopContinueValue() != null ? config.getLoopContinueValue() : "1";
                String currentStatus = new String(response, offset, contVal.length(), StandardCharsets.UTF_8);
                return contVal.equals(currentStatus);
        }
    }

    /**
     * Prepares the next request message based on the configured Request Strategy.
     */
    private byte[] prepareNextRequest(ChunkPaginationConfig config, byte[] response) {
        PaginationRequestStrategy strategy = config.getRequestStrategy() != null ?
                config.getRequestStrategy() : PaginationRequestStrategy.REPLAY;

        // Clone the original request to prevent reference contamination
        byte[] nextReq = this.lastRequest.clone();

        switch (strategy) {
            case INCREMENT_PAGE:
                if (config.getPageOffset() != null) {
                    int offset = config.getPageOffset();
                    int len = (config.getPageLen() != null) ? config.getPageLen() : 1;

                    try {
                        // 1. Extract current page number string
                        String currentStr = new String(nextReq, offset, len, StandardCharsets.UTF_8).trim();

                        // 2. Increment numerical value
                        int nextVal = Integer.parseInt(currentStr) + 1;

                        // 3. Format back to string with left-padded zeros
                        String nextStr = String.format("%0" + len + "d", nextVal);
                        byte[] nextStrBytes = nextStr.getBytes(StandardCharsets.UTF_8);

                        // 4. Overwrite the page field in the request telegram
                        System.arraycopy(nextStrBytes, 0, nextReq, offset, Math.min(nextStrBytes.length, len));

                        // 5. Update state for the next potential loop
                        this.lastRequest = nextReq;

                    } catch (Exception e) {
                        log.error("Failed to increment page number at offset {}: {}", offset, e.getMessage());
                    }
                }
                break;

            case NEXT_KEY_EXCHANGE:
                // Extract a key from the current response and inject it into the next request
                if (config.getResKeyOffset() != null && config.getReqKeyOffset() != null) {
                    System.arraycopy(response, config.getResKeyOffset(), nextReq, config.getReqKeyOffset(), config.getKeyLen());
                    this.lastRequest = nextReq;
                }
                break;

            case REPLAY:
            default:
                // Simply resend the original request
                break;
        }
        return nextReq;
    }

    /**
     * Aggregates the collected data and fires it downstream.
     */
    private void finalizeAndFire(ChannelHandlerContext ctx) {
        if (mergedBodyBuffer != null && mergedBodyBuffer.readableBytes() > 0) {
            byte[] totalBody = new byte[mergedBodyBuffer.readableBytes()];
            mergedBodyBuffer.readBytes(totalBody);

            // Combine the first response header with all collected bodies
            ByteBuf finalMsg = Unpooled.wrappedBuffer(firstHeader, totalBody);
            ctx.fireChannelRead(finalMsg);
        }
        clear();
    }

    /**
     * Resets local state and releases buffers to prevent memory leaks.
     */
    private void clear() {
        this.firstHeader = null;
        this.currentLoopCount = 0;
        if (mergedBodyBuffer != null) {
            if (mergedBodyBuffer.refCnt() > 0) mergedBodyBuffer.release();
            mergedBodyBuffer = null;
        }
        this.lastRequest = null;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        clear();
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        clear();
        super.exceptionCaught(ctx, cause);
    }
}
