package io.zefio.gateway.netty.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.zefio.core.GatewayPlugin;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.FramingField;
import io.zefio.core.payload.builder.config.TelegramValues;
import io.zefio.gateway.netty.dto.NettyValues;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Standard length-field decoder for fixed-length protocols.
 * Parses an ASCII length header to extract precisely sized frames.
 */
public class LengthFrameDecoder extends AbstractByteToMessageDecoder {

    private final boolean lengthDataInclude;
    private final int lengthDataSize;
    private final long maxContentLength;

    public LengthFrameDecoder(String flowName, GatewayPlugin flowNode, NettyValues values,
                              PayloadBuilder eventBuilder, Charset requestEncoding, Charset responseEncoding) {
        super(flowName, flowNode, values, requestEncoding, responseEncoding);

        TelegramValues telegramValues = eventBuilder.getTelegram().getValues();
        FramingField framing = telegramValues.getFraming();

        if (framing == null || framing.getLengthDataSize() == null || framing.getLengthDataSize() <= 0) {
            throw new DecoderException("Invalid lengthDataSize: value must be positive.");
        }

        this.lengthDataSize = framing.getLengthDataSize();
        this.lengthDataInclude = Boolean.TRUE.equals(framing.getLengthDataInclude());
        this.maxContentLength = values.getMaxContentLength();
    }

    @Override
    protected ByteBuf decodeFrame(ByteBuf in) throws Exception {
        // Ensure the header itself is fully readable
        if (in.readableBytes() < lengthDataSize) return null;

        in.markReaderIndex();

        byte[] lengthBytes = new byte[lengthDataSize];
        in.readBytes(lengthBytes);
        String lengthStr = new String(lengthBytes, StandardCharsets.US_ASCII);

        // 1. Parse length field (ASCII-based)
        int bodyLength;
        try {
            bodyLength = Integer.parseInt(lengthStr.trim());
        } catch (NumberFormatException e) {
            log.error("Invalid length field [{}]. Termination required.", lengthStr);
            throw new DecoderException("Unable to parse length field: " + lengthStr);
        }

        // 2. Adjust body length based on whether the header is included in the count
        bodyLength = lengthDataInclude ? bodyLength - lengthDataSize : bodyLength;

        // 3. Validation and DoS Protection
        if (bodyLength < 0) {
            log.error("Negative body length detected [{}].", bodyLength);
            throw new DecoderException("Body length must be non-negative.");
        }
        if (bodyLength > maxContentLength) {
            log.error("Body length [{}] exceeds max threshold [{}].", bodyLength, maxContentLength);
            throw new DecoderException("Message size exceeded allowed limit.");
        }

        // 4. Verification: check if the entire message has arrived
        if (in.readableBytes() < bodyLength) {
            in.resetReaderIndex();
            log.debug("Insufficient data. Need: {}, Available: {}", bodyLength, in.readableBytes());
            return null;
        }

        // 5. Slice and return the full frame (Header + Body)
        ByteBuf fullFrame = in.slice(in.readerIndex() - lengthDataSize, lengthDataSize + bodyLength).retain();
        in.skipBytes(bodyLength); // Advance reader pointer to the next packet boundary

        log.debug("Decoded full frame: size={}", lengthDataSize + bodyLength);
        return fullFrame;
    }
}
