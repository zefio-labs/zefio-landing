package io.zefio.gateway.netty.decoder;

import io.netty.buffer.ByteBuf;
import io.zefio.core.GatewayPlugin;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.FramingField;
import io.zefio.core.payload.builder.config.TelegramValues;
import io.zefio.core.payload.util.BytesUtils;
import io.zefio.gateway.netty.dto.NettyValues;

import java.nio.charset.Charset;

/**
 * Decoder that identifies message boundaries using a specific delimiter.
 * If a delimiter is not defined, it treats the entire readable buffer as a single frame.
 */
public class DelimiterPollingDecoder extends AbstractPollingDecoder {

    private final byte[] delimiter;

    public DelimiterPollingDecoder(String flowName, GatewayPlugin flowNode, NettyValues values,
                                   PayloadBuilder eventBuilder, Charset requestEncoding, Charset responseEncoding) {
        super(flowName, flowNode, values, values.getPolling(), requestEncoding, responseEncoding);

        TelegramValues telegramValues = eventBuilder.getTelegram().getValues();
        FramingField framing = telegramValues.getFraming();

        String delimStr = (framing != null) ? framing.getDelimiter() : null;
        this.delimiter = (delimStr != null) ? delimStr.getBytes(requestEncoding) : null;
    }

    @Override
    protected ByteBuf decodeFrame(ByteBuf in) throws Exception {
        // If no delimiter is configured, process the entire buffer as one frame
        if (delimiter == null) {
            if (in.readableBytes() == 0) return null;
            ByteBuf frame = in.readRetainedSlice(in.readableBytes());
            in.clear();
            return frame;
        }

        int delimIndex = indexOf(in, delimiter);

        // Wait if the terminating delimiter has not yet arrived
        if (delimIndex < 0) return null;

        int frameLength = delimIndex - in.readerIndex();
        ByteBuf frame = in.readRetainedSlice(frameLength);
        in.skipBytes(delimiter.length); // Skip the delimiter for the next frame

        byte[] msgBytes = new byte[frame.readableBytes()];
        frame.getBytes(0, msgBytes);

        // Filter and discard polling heartbeats
        if (isPollingRequest(msgBytes)) {
            frame.release();
            return null;
        }
        return frame;
    }

    /**
     * Extracts the message for polling validation. Since delimiter-based protocols
     * lack a length header, the raw bytes are used.
     */
    @Override
    protected byte[] extractPollingMessage(byte[] rawBytes) {
        return rawBytes;
    }

    /**
     * Appends the configured delimiter to the response body for polling replies.
     */
    @Override
    protected byte[] formatPollingResponse(byte[] responseBody) {
        if (delimiter != null) {
            return BytesUtils.bytesMerge(responseBody, delimiter);
        }
        // Fallback to newline if no delimiter is explicitly set
        return BytesUtils.bytesMerge(responseBody, "\n".getBytes(responseEncoding));
    }

    /**
     * Searches for the byte array delimiter within a ByteBuf.
     */
    private int indexOf(ByteBuf buffer, byte[] delim) {
        for (int i = buffer.readerIndex(); i <= buffer.writerIndex() - delim.length; i++) {
            boolean match = true;
            for (int j = 0; j < delim.length; j++) {
                if (buffer.getByte(i + j) != delim[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }
}
