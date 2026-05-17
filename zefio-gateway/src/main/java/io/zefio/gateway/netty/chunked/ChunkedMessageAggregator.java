package io.zefio.gateway.netty.chunked;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.zefio.core.payload.util.BytesUtils;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Logic for identifying chunk states and aggregating byte segments into a final message.
 */
public class ChunkedMessageAggregator {
    private final int statusOffset;
    private final int longMessageOffset;
    private final int lengthDataSize;
    private final boolean lengthDataInclude;
    private final Charset charset;
    private final String statusEnd;

    private final Map<String, ChunkState> statusMap = new HashMap<>();
    private final Map<ChunkState, Integer> statusLengthMap = new HashMap<>();

    public ChunkedMessageAggregator(
            int statusOffset,
            String statusStart,
            String statusMiddle,
            String statusEnd,
            int lengthDataSize,
            boolean lengthDataInclude,
            int longMessageOffset,
            Charset charset
    ) {
        this.statusOffset = statusOffset;
        this.longMessageOffset = longMessageOffset;
        this.lengthDataSize = lengthDataSize;
        this.lengthDataInclude = lengthDataInclude;
        this.charset = charset;
        this.statusEnd = statusEnd;

        // Map status strings to internal state Enums
        statusMap.put(statusStart, ChunkState.START);
        statusMap.put(statusMiddle, ChunkState.MIDDLE);
        statusMap.put(statusEnd, ChunkState.END);

        statusLengthMap.put(ChunkState.START, statusStart.length());
        statusLengthMap.put(ChunkState.MIDDLE, statusMiddle.length());
        statusLengthMap.put(ChunkState.END, statusEnd.length());
    }

    /**
     * Detects the state of the incoming data chunk.
     */
    public ChunkState detect(byte[] data) {
        if (data == null) return ChunkState.UNKNOWN;

        for (Map.Entry<String, ChunkState> entry : statusMap.entrySet()) {
            String status = entry.getKey();
            ChunkState state = entry.getValue();
            int len = statusLengthMap.get(state);

            if (data.length >= statusOffset + len) {
                String sub = new String(data, statusOffset, len, charset);
                if (status.equals(sub)) return state;
            }
        }
        return ChunkState.SINGLE; // Not a multi-part message or unrecognized flag
    }

    /**
     * Appends the initial chunk (START), including its header.
     */
    public void appendStart(ByteBuf buffer, byte[] data) {
        buffer.writeBytes(data);
    }

    /**
     * Extracts and appends only the body portion of subsequent chunks (MIDDLE/END).
     */
    public void appendBody(ByteBuf buffer, byte[] data) {
        int bodyStart = longMessageOffset;
        int bodyLen = Math.max(0, data.length - bodyStart);
        if (bodyLen > 0) {
            buffer.writeBytes(data, bodyStart, bodyLen);
        }
    }

    /**
     * Finalizes the aggregation, updates the status to END, and recalculates the total length.
     */
    public ByteBuf buildAggregated(ByteBuf buffer) {
        byte[] aggregated = new byte[buffer.readableBytes()];
        buffer.getBytes(0, aggregated);

        byte[] endBytes = statusEnd.getBytes(charset);

        // Apply the final status flag to the header
        if (aggregated.length >= statusOffset + endBytes.length) {
            System.arraycopy(endBytes, 0, aggregated, statusOffset, endBytes.length);
        }

        // Recalculate framing length based on the total aggregated size
        aggregated = BytesUtils.updateLength(aggregated, lengthDataSize, lengthDataInclude);

        return Unpooled.wrappedBuffer(aggregated);
    }
}
