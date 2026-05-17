package io.zefio.gateway.netty.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.zefio.core.GatewayPlugin;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.FramingField;
import io.zefio.core.payload.builder.config.TelegramValues;
import io.zefio.gateway.netty.dto.NettyValues;

import java.nio.charset.Charset;

/**
 * Netty decoder implementation for delimiter-based framing.
 * Slices the byte stream based on a predefined delimiter sequence.
 */
public class DelimiterFrameDecoder extends AbstractByteToMessageDecoder {

    private final byte[] delimiter;

    public DelimiterFrameDecoder(String flowName, GatewayPlugin flowNode, NettyValues values, PayloadBuilder eventBuilder, Charset requestEncoding, Charset responseEncoding) {
        super(flowName, flowNode, values, requestEncoding, responseEncoding);

        TelegramValues telegramValues = eventBuilder.getTelegram().getValues();
        FramingField framing = telegramValues.getFraming();

        String delimStr = (framing != null) ? framing.getDelimiter() : null;
        this.delimiter = (delimStr != null) ? delimStr.getBytes(requestEncoding) : null;
    }

    @Override
    protected ByteBuf decodeFrame(ByteBuf in) throws Exception {
        // If no delimiter is defined, treat the entire available buffer as a single frame
        if (delimiter == null) {
            if (in.readableBytes() == 0) return null;
            ByteBuf frame = in.readRetainedSlice(in.readableBytes());
            in.clear();
            return frame;
        }

        // Safeguard against extremely large payloads without delimiters (OOM prevention)
        if (in.readableBytes() > values.getMaxContentLength()) {
            log.error("Discarding data: No delimiter found within {} bytes", values.getMaxContentLength());
            in.clear();
            throw new DecoderException("Delimiter not found within max content length limit");
        }

        int delimIndex = indexOf(in, delimiter);

        // Wait for more data if the delimiter has not arrived yet
        if (delimIndex < 0) return null;

        // Calculate logical message size excluding the delimiter
        int frameLength = delimIndex - in.readerIndex();
        ByteBuf frame = in.readRetainedSlice(frameLength);

        // Advance the reader index past the delimiter
        in.skipBytes(delimiter.length);

        return frame;
    }

    /**
     * Searches for the byte sequence of the delimiter within the ByteBuf.
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
