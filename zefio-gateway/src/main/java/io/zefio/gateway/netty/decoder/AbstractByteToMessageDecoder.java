package io.zefio.gateway.netty.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.zefio.core.common.base.MDCKey;
import io.zefio.core.GatewayPlugin;
import io.zefio.gateway.netty.NettyMdcConstants;
import io.zefio.gateway.netty.dto.NettyValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/**
 * Base decoder for Netty-based Ingress/Upstream communication.
 * Handles MDC context restoration to ensure logs are traceable during the decoding phase.
 */
public abstract class AbstractByteToMessageDecoder extends ByteToMessageDecoder {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final String flowName;
    protected final GatewayPlugin module;
    protected final NettyValues values;
    protected Charset requestEncoding;
    protected Charset responseEncoding;

    public AbstractByteToMessageDecoder(String flowName, GatewayPlugin module, NettyValues values, Charset requestEncoding, Charset responseEncoding) {
        this.flowName = flowName;
        this.module = module;
        this.values = values;
        this.requestEncoding = requestEncoding;
        this.responseEncoding = responseEncoding;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() == 0) return;

        // Restore MDC context from the channel attributes to maintain transaction traceability
        Map<String, String> mdcContext = ctx.channel().attr(NettyMdcConstants.MDC_CONTEXT_KEY).get();
        if (mdcContext != null) {
            MDC.setContextMap(mdcContext);
        } else {
            // Use Channel ID (CID) for initial log tracking before a Transaction ID (TID) is generated
            MDC.put(MDCKey.CID.name(), ctx.channel().id().asShortText());
        }

        try {
            int readBytesLength = in.readableBytes();
            log.debug("Netty decoder: {} bytes available for reading.", readBytesLength);

            // Delegate actual frame extraction to concrete implementations
            ByteBuf frame = decodeFrame(in);
            if (frame != null) {
                out.add(frame);
            }
        } finally {
            // Always clear MDC to prevent context leakage across different Netty events
            MDC.clear();
        }
    }

    /**
     * Extracts the message frame from the input buffer.
     * Must be implemented by specialized decoders (e.g., FixedLength, DelimiterBased).
     *
     * @param in the input ByteBuf
     * @return the extracted frame as a ByteBuf, or null if more data is required
     */
    protected abstract ByteBuf decodeFrame(ByteBuf in) throws Exception;
}
