package io.zefio.gateway.netty.encoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.zefio.core.payload.Payload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Final stage encoder that converts Payload, ByteBuf, or byte arrays
 * into a Netty-compatible Unpooled buffer for physical transmission.
 */
public class DefaultByteArrayEncoder extends MessageToMessageEncoder<Object> {

	protected final Logger log = LoggerFactory.getLogger(getClass());

	public DefaultByteArrayEncoder() {
		// Default constructor
	}

	@Override
	protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
		byte[] result = null;

		if (msg instanceof Payload) {
			// Extract the body from the Zefio Payload
			Payload payload = (Payload) msg;
			result = payload.getBody();
		}
		else if (msg instanceof ByteBuf) {
			// Drain a ByteBuf into a byte array
			ByteBuf event = (ByteBuf) msg;
			result = new byte[event.readableBytes()];
			event.readBytes(result);
		}
		else if (msg instanceof byte[]) {
			// Use provided byte array directly
			result = (byte[]) msg;
		}

		if (result != null) {
			out.add(Unpooled.wrappedBuffer(result));
		}
	}
}
