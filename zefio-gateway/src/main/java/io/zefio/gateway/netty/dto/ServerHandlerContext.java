package io.zefio.gateway.netty.dto;

import io.zefio.core.Ingress;
import io.zefio.core.IngressHandler;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.gateway.netty.transaction.ITxnManager;
import lombok.Getter;

/**
 * Context provided to Ingress (server-side) Netty handlers.
 * Encapsulates necessary components for transaction management and flow orchestration.
 */
@Getter
public class ServerHandlerContext<T> {

    private final String flowName;
    private final Ingress ingress;
    private final Object values;
    private final NettyValues nettyValues; // Common network configuration access
    private final ITxnManager<T> txnManager; // Utilized by terminal handlers for correlation
    private final IngressHandler ingressHandler; // Entry point for the core engine pipeline
    private final PayloadBuilder eventBuilder;

    public ServerHandlerContext(String flowName, Ingress ingress, Object values,
                                NettyValues nettyValues, ITxnManager<T> txnManager,
                                IngressHandler ingressHandler, PayloadBuilder eventBuilder) {
        this.flowName = flowName;
        this.ingress = ingress;
        this.values = values;
        this.nettyValues = nettyValues;
        this.txnManager = txnManager;
        this.ingressHandler = ingressHandler;
        this.eventBuilder = eventBuilder;
    }
}
