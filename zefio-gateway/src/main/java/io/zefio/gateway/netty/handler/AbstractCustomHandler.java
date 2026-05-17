package io.zefio.gateway.netty.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.TypeParameterMatcher;
import io.zefio.core.GatewayPlugin;
import io.zefio.core.IngressHandler;
import io.zefio.gateway.netty.dto.ClientHandlerContext;
import io.zefio.gateway.netty.dto.HandlerDefinition;
import io.zefio.gateway.netty.dto.ServerHandlerContext;
import io.zefio.gateway.netty.transaction.ITxnManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A flexible Duplex handler supporting both Ingress and Upstream contexts.
 * Includes automatic resource management and type-safe message matching.
 */
public abstract class AbstractCustomHandler<T> extends ChannelDuplexHandler {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final String flowName;
    protected final GatewayPlugin module;
    protected final ITxnManager<T> txnManager; // Nullable if not a terminal handler
    protected final Object values; // e.g., TcpServerValues
    protected IngressHandler ingressHandler; // Nullable if not a terminal handler
    protected final HandlerDefinition handlerDef;

    private final TypeParameterMatcher matcher;

    public AbstractCustomHandler(Object contextObj, HandlerDefinition handlerDef) {
        // Create matcher for generic type T (replicating SimpleChannelInboundHandler logic)
        this.matcher = TypeParameterMatcher.find(this, AbstractCustomHandler.class, "T");

        if (contextObj instanceof ServerHandlerContext) {
            ServerHandlerContext context = (ServerHandlerContext) contextObj;
            this.flowName = context.getFlowName();
            this.module = context.getIngress();
            this.txnManager = (ITxnManager<T>) context.getTxnManager();
            this.values = context.getValues();
            this.ingressHandler = context.getIngressHandler();
            this.handlerDef = handlerDef;
        } else {
            ClientHandlerContext context = (ClientHandlerContext) contextObj;
            this.flowName = context.getFlowName();
            this.module = context.getUpstream();
            this.txnManager = (ITxnManager<T>) context.getTxnManager();
            this.values = context.getValues();
            this.handlerDef = handlerDef;
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (matcher.match(msg)) {
            try {
                @SuppressWarnings("unchecked")
                T castMsg = (T) msg;
                channelRead0(ctx, castMsg);
            } finally {
                // Prevent resource leaks by releasing the message after processing
                ReferenceCountUtil.release(msg);
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    /** Signature maintained for compatibility with SimpleChannelInboundHandler logic. */
    protected abstract void channelRead0(ChannelHandlerContext ctx, T msg) throws Exception;
}
