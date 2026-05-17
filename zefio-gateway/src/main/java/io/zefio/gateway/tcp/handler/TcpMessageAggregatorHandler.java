package io.zefio.gateway.tcp.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.zefio.core.Upstream;
import io.zefio.core.schema.dto.RequestEncodingSupport;
import io.zefio.core.Ingress;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.FramingField;
import io.zefio.core.payload.builder.config.TelegramValues;
import io.zefio.gateway.netty.chunked.ChunkAggregatorTimeoutManager;
import io.zefio.gateway.netty.chunked.ChunkState;
import io.zefio.gateway.netty.chunked.ChunkedMessageAggregator;
import io.zefio.gateway.netty.chunked.dto.ChunkAggregatorConfig;
import io.zefio.gateway.netty.dto.HandlerDefinition;
import io.zefio.gateway.netty.handler.AbstractCustomHandler;

import java.nio.charset.Charset;

/**
 * TCP Message Aggregator Handler
 * Responsibility: Reassembles fragmented TCP chunks into a single message.
 * Supports: START, MIDDLE, END, and SINGLE message states.
 */
public class TcpMessageAggregatorHandler extends AbstractCustomHandler<ByteBuf> {
    protected final Charset requestEncoding;
    protected int maxMessageSize;
    protected ChunkAggregatorTimeoutManager timeoutManager;
    protected ChunkedMessageAggregator aggregator;

    public TcpMessageAggregatorHandler(Object context, HandlerDefinition handlerDef) {
        super(context, handlerDef);

        // Initialize encoding based on request support configuration
        this.requestEncoding = ((RequestEncodingSupport) values).getRequestEncoding();

        ChunkAggregatorConfig cfg = handlerDef.getAggregator();
        if (cfg != null) {
            // Determine the event builder based on Ingress or Upstream module type
            PayloadBuilder eventBuilder = module instanceof Ingress
                    ? ((Ingress) module).getEventBuilder()
                    : ((Upstream) module).getEventBuilder();

            TelegramValues telegramValues = eventBuilder.getTelegram().getValues();
            FramingField framing = telegramValues.getFraming();

            int lengthDataSize = (framing != null && framing.getLengthDataSize() != null) ? framing.getLengthDataSize() : 0;
            boolean lengthDataInclude = (framing != null && Boolean.TRUE.equals(framing.getLengthDataInclude()));

            // Initialize the aggregator with protocol-specific framing rules
            this.aggregator = new ChunkedMessageAggregator(
                    cfg.getStatusOffset(),
                    cfg.getStatusStart(),
                    cfg.getStatusMiddle(),
                    cfg.getStatusEnd(),
                    lengthDataSize,
                    lengthDataInclude,
                    cfg.getLongMessageOffset(),
                    requestEncoding
            );

            this.maxMessageSize = cfg.getMaxMessageSize();
            this.timeoutManager = new ChunkAggregatorTimeoutManager(cfg.getChunkTimeout());
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        String channelId = ctx.channel().id().asShortText();

        byte[] datas = new byte[msg.readableBytes()];
        msg.readBytes(datas);

        log.info("[{}] Received chunk len={} data={}", channelId, datas.length, new String(datas, requestEncoding));

        // Refresh session timeout upon receiving a new chunk
        timeoutManager.refresh(channelId);

        // Detect the state of the incoming chunk
        ChunkState state = aggregator.detect(datas);

        switch (state) {
            case START: {
                // Clear any existing stale session data and start a new sequence
                timeoutManager.clearSession(channelId);
                ChunkAggregatorTimeoutManager.SessionWrapper session = timeoutManager.getOrCreate(channelId);

                aggregator.appendStart(session.getBuffer(), datas);
                log.debug("[{}] START chunk received, totalLen={}", channelId, datas.length);
                return;
            }
            case MIDDLE: {
                ChunkAggregatorTimeoutManager.SessionWrapper session = timeoutManager.getOrCreate(channelId);

                // Extract body area and append to existing buffer
                aggregator.appendBody(session.getBuffer(), datas);

                // Defensive check: Prevent memory exhaustion if the message exceeds limits
                if (session.getBuffer().readableBytes() > maxMessageSize) {
                    log.error("[{}] Chunk buffer exceeded maxMessageSize. Clearing session.", channelId);
                    timeoutManager.clearSession(channelId);
                }
                return;
            }
            case END: {
                ChunkAggregatorTimeoutManager.SessionWrapper session = timeoutManager.getOrCreate(channelId);

                // Append the final body part
                aggregator.appendBody(session.getBuffer(), datas);

                // Build the final aggregated message
                ByteBuf completeBuf = aggregator.buildAggregated(session.getBuffer());

                // Remove the session to stop the timeout manager
                timeoutManager.remove(channelId);

                // Pass the fully assembled message to the next handler in the pipeline
                ctx.fireChannelRead(completeBuf);
                log.debug("[{}] END chunk aggregated and passed.", channelId);
                return;
            }

            case SINGLE:
            case UNKNOWN:
            default:
                // Handle messages that are not chunked or have unknown status as single frames
                ctx.fireChannelRead(Unpooled.wrappedBuffer(datas));
                return;
        }
    }

    /**
     * Cleanup session buffers when the channel becomes inactive.
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String channelId = ctx.channel().id().asShortText();
        ByteBuf buf = timeoutManager.remove(channelId);
        if (buf != null) {
            buf.release();
        }
        super.channelInactive(ctx);
    }
}
