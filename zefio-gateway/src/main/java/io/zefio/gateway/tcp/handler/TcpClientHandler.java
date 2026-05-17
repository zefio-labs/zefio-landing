package io.zefio.gateway.tcp.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ZefioMessage;
import io.zefio.gateway.netty.dto.ClientHandlerContext;
import io.zefio.gateway.netty.dto.HandlerDefinition;
import io.zefio.gateway.netty.handler.AbstractUpstreamHandler;
import io.zefio.gateway.netty.util.NettyUtils;

/**
 * Terminal TCP client handler for Upstream communication.
 * Slices the incoming ByteBuf into a Zefio Payload and resolves the associated transaction.
 */
public class TcpClientHandler extends AbstractUpstreamHandler<ByteBuf, Payload> {

    public TcpClientHandler(ClientHandlerContext<Payload> context, HandlerDefinition handlerDef) {
        super(context);
    }

    @Override
    protected void handleRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        // 1. Security: Enforce content length limits at the edge
        if (msg.readableBytes() > maxContentLength) {
            handleExceededLength(ctx, msg.readableBytes());
            return;
        }

        // 2. Data Extraction: Drain the Netty buffer into a raw byte array
        byte[] datas = new byte[msg.readableBytes()];
        msg.readBytes(datas);

        // 3. Event Promotion: Wrap the raw bytes into a ZefioMessage (Payload)
        // The startTime of this event represents the precise completion of network I/O.
        Payload responsePayload = new ZefioMessage(datas, responseEncoding);

        // Ensure the payload is prepared for Lazy Parsing by setting the telegram name from context
        responsePayload.setTelegramName(this.context.getTelegramName());

        // 4. Transaction Resolution: Notify the TxnManager for Two-Way flows
        if (upstream.isTwoWay()) {
            // Drop the raw bytes into the transaction manager as a fully formed Event
            this.txnManager.complete(ctx.channel(), responsePayload);
        }

        // Logging with MDC restoration for traceability
        NettyUtils.runWithMdc(ctx.channel(), () -> {
            log.info("TCP Upstream received (TwoWay: {}). Bytes: [{}].",
                    upstream.isTwoWay(), datas.length);
        });
    }
}
