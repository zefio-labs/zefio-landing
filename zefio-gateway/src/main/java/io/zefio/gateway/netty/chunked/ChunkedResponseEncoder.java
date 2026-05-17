package io.zefio.gateway.netty.chunked;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.Upstream;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.builder.config.FramingField;
import io.zefio.core.payload.builder.config.FramingType;
import io.zefio.core.payload.builder.config.TelegramValues;
import io.zefio.core.payload.util.BytesUtils;
import io.zefio.gateway.netty.IngressSender;
import io.zefio.gateway.netty.chunked.dto.ChunkSplitterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

/**
 * Encodes large payloads into multiple TCP chunks.
 * Handles header replication, status flag updates, and ensures character encoding integrity.
 */
public class ChunkedResponseEncoder implements ChunkedResponseEncoderStrategy {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final Charset encoding;
    private final Upstream upstream;
    private final boolean keepAlive;
    private final IngressSender sender;

    protected Integer maxChunkSize;
    protected Integer statusOffset;
    protected String statusStart;
    protected String statusMiddle;
    protected String statusEnd;
    protected Integer headerOffset;
    protected Integer headerLength;
    protected int longMessageOffset;

    public ChunkedResponseEncoder(Upstream upstream,
                                  ChunkSplitterConfig splitter,
                                  Charset encoding,
                                  boolean keepAlive,
                                  IngressSender sender) {
        this.upstream = upstream;
        this.encoding = encoding;
        this.keepAlive = keepAlive;
        this.sender = sender;

        if (splitter != null && splitter.isBigMessage()) {
            this.maxChunkSize = splitter.getMaxChunkSize();
            this.statusOffset = splitter.getStatusOffset();
            this.statusStart = splitter.getStatusStart();
            this.statusMiddle = splitter.getStatusMiddle();
            this.statusEnd = splitter.getStatusEnd();
            this.headerOffset = splitter.getHeaderOffset();
            this.headerLength = splitter.getHeaderLength();
            this.longMessageOffset = splitter.getLongMessageOffset();
        }
    }

    @Override
    public void sendChunkedResponse(Payload payload, ChannelHandlerContext ctx) {
        byte[] fullData = payload.getBody();
        int lengthDataSize = 0;
        boolean lengthDataInclude = false;

        // Retrieve framing metadata from the upstream configuration
        TelegramValues telegramValues = this.upstream.getEventBuilder().getTelegram().getValues();
        if (telegramValues != null) {
            FramingField framing = telegramValues.getFraming();
            if (framing != null && framing.getType() == FramingType.Length) {
                lengthDataSize = framing.getLengthDataSize() != null ? framing.getLengthDataSize() : 0;
                lengthDataInclude = Boolean.TRUE.equals(framing.getLengthDataInclude());
            }
        }

        // Use ByteBuf wrapper for zero-copy slicing
        ByteBuf fullBuf = Unpooled.wrappedBuffer(fullData);

        try {
            // 1. Extract the header segment
            ByteBuf headerBuf = getHeaderBuf(fullBuf);

            // 2. Locate the long message body segment
            ByteBuf longMsgBuf = getLongMessageBuf(fullBuf, lengthDataSize);

            // 3. Initiate chunked transmission
            sendChunkedPackets(payload, ctx, headerBuf, longMsgBuf, lengthDataSize, lengthDataInclude);

        } finally {
            fullBuf.release();
        }
    }

    private ByteBuf getHeaderBuf(ByteBuf fullBuf) {
        if (headerOffset != null && headerLength != null) {
            return fullBuf.slice(headerOffset, headerLength);
        } else {
            return Unpooled.EMPTY_BUFFER;
        }
    }

    private ByteBuf getLongMessageBuf(ByteBuf fullBuf, int lengthDataSize) {
        int start = (headerOffset != null && headerLength != null) ? longMessageOffset : lengthDataSize;
        int len = fullBuf.readableBytes() - start;
        return fullBuf.slice(start, len);
    }

    /**
     * Calculates a safe split length to prevent multi-byte character corruption.
     * Iteratively reduces length until the byte sequence represents a complete set of characters.
     */
    private int findSafeSplitByChar(byte[] data, int offset, int maxBytes, Charset charset) {
        if (offset >= data.length) return 0;

        int safeLength = Math.min(maxBytes, data.length - offset);
        CharsetDecoder decoder = charset.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);

        while (safeLength > 0) {
            try {
                decoder.decode(ByteBuffer.wrap(data, offset, safeLength));
                break;
            } catch (CharacterCodingException e) {
                safeLength--;
            }
        }
        return safeLength > 0 ? safeLength : 1;
    }

    private void sendChunkedPackets(Payload payload, ChannelHandlerContext ctx, ByteBuf headerBuf, ByteBuf longMsgBuf,
                                    int lengthDataSize, boolean lengthDataInclude) {

        int longMessageLength = longMsgBuf.readableBytes();
        int offset = 0;
        int chunkIndex = 0;

        while (offset < longMessageLength) {
            // Calculate maximum available space for the body in this chunk
            int maxBodySize = maxChunkSize - lengthDataSize - headerBuf.readableBytes();
            if (maxBodySize <= 0) {
                throw new FlowException(FlowResultStatus.MESSAGE_FORMAT_ERROR,
                        "Configured maxChunkSize (" + maxChunkSize + ") is too small to accommodate the header and length metadata");
            }

            // Calculate safe split length to maintain character integrity
            byte[] rawSegment = ByteBufUtil.getBytes(longMsgBuf, offset, Math.min(maxBodySize, longMessageLength - offset), false);
            int chunkBodyLength = findSafeSplitByChar(rawSegment, 0, rawSegment.length, this.encoding);

            // Slice the body using zero-copy
            ByteBuf chunkBodyBuf = longMsgBuf.slice(offset, chunkBodyLength);

            // Create a copy of the header for this chunk
            ByteBuf chunkHeaderBuf = Unpooled.buffer(headerBuf.readableBytes());
            chunkHeaderBuf.writeBytes(headerBuf, 0, headerBuf.readableBytes());

            // Merge header and body segments
            ByteBuf packetWithoutLength = Unpooled.wrappedBuffer(chunkHeaderBuf, chunkBodyBuf);
            byte[] mergedArray = ByteBufUtil.getBytes(packetWithoutLength);

            // Determine chunk state (START, MIDDLE, END)
            String status;
            if (offset == 0 && chunkBodyLength >= longMessageLength) {
                status = statusEnd;
            } else if (offset == 0) {
                status = statusStart;
            } else if (offset + chunkBodyLength >= longMessageLength) {
                status = statusEnd;
            } else {
                status = statusMiddle;
            }

            byte[] statusBytes = status.getBytes(this.encoding);
            int absoluteStatusOffset = statusOffset - lengthDataSize;

            // Apply status flag if within array bounds
            if (absoluteStatusOffset >= 0 && absoluteStatusOffset + statusBytes.length <= mergedArray.length) {
                System.arraycopy(statusBytes, 0, mergedArray, absoluteStatusOffset, statusBytes.length);
            } else {
                log.warn("Status offset out of range: absoluteOffset={}, length={}, packetLen={}",
                        absoluteStatusOffset, statusBytes.length, mergedArray.length);
            }

            // Prepend length metadata if applicable
            byte[] packet = BytesUtils.appendLength(mergedArray, lengthDataSize, lengthDataInclude);

            // Dispatch chunk via the InboundSender
            if (offset + chunkBodyLength >= longMessageLength) {
                sender.lastCompleteAndSend(payload, ctx, keepAlive, packet);
            } else {
                sender.sendChunk(payload, ctx, packet);
            }

            if (log.isDebugEnabled()) {
                log.debug("Dispatched TCP chunk [{}]: length={}, status={}, offset={}",
                        ++chunkIndex, packet.length, status, offset);
            }

            offset += chunkBodyLength;
        }
    }
}
