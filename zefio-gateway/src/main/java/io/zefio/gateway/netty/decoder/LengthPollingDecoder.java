package io.zefio.gateway.netty.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.zefio.core.GatewayPlugin;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.FramingField;
import io.zefio.core.payload.builder.config.TelegramValues;
import io.zefio.core.payload.util.BytesUtils;
import io.zefio.gateway.netty.dto.NettyValues;

import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * Specialized polling decoder for length-prefixed protocols.
 * Intercepts heartbeat requests by identifying polling patterns after the length header.
 */
public class LengthPollingDecoder extends AbstractPollingDecoder {

	private final boolean lengthDataInclude;
	private final int lengthDataSize;

	public LengthPollingDecoder(String flowName, GatewayPlugin flowNode, NettyValues values,
								PayloadBuilder eventBuilder, Charset requestEncoding, Charset responseEncoding) {
		super(flowName, flowNode, values, values.getPolling(), requestEncoding, responseEncoding);

		TelegramValues telegramValues = eventBuilder.getTelegram().getValues();
		FramingField framing = telegramValues.getFraming();

		if (framing == null || framing.getLengthDataSize() == null || framing.getLengthDataSize() <= 0) {
			throw new DecoderException("Invalid lengthDataSize: value must be positive.");
		}

		this.lengthDataSize = framing.getLengthDataSize();
		this.lengthDataInclude = Boolean.TRUE.equals(framing.getLengthDataInclude());
	}

	@Override
	protected ByteBuf decodeFrame(ByteBuf in) throws Exception {
		byte[] data = new byte[in.readableBytes()];
		in.readBytes(data);

		// If the packet is identified as a polling request, it is handled internally and not passed forward
		if (isPollingRequest(data)) {
			return null;
		}

		return Unpooled.wrappedBuffer(data);
	}

	/**
	 * Extracts the core message content by skipping the length header.
	 * This is used to compare the incoming data against the configured polling request pattern.
	 */
	@Override
	protected byte[] extractPollingMessage(byte[] rawBytes) {
		byte[] patternBytes = pollingRequest.getBytes();
		if (rawBytes.length < lengthDataSize + patternBytes.length) {
			return null;
		}
		// Extract the segment starting after the length header
		return Arrays.copyOfRange(rawBytes, lengthDataSize, lengthDataSize + patternBytes.length);
	}

	/**
	 * Wraps the polling response body with the appropriate length header before transmission.
	 */
	@Override
	protected byte[] formatPollingResponse(byte[] responseBody) {
		return BytesUtils.appendLength(responseBody, this.lengthDataSize, this.lengthDataInclude);
	}
}
