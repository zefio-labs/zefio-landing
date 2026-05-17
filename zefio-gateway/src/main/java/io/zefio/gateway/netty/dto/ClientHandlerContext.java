package io.zefio.gateway.netty.dto;

import io.zefio.core.Upstream;
import io.zefio.gateway.netty.transaction.ITxnManager;
import lombok.Getter;

/**
 * Context object provided to client-side Netty handlers.
 * Maintains references to Upstream configurations, shared Netty settings, and transaction management.
 */
@Getter
public class ClientHandlerContext<T> {

    private final String flowName;
    private final Upstream upstream;
    private final Object values;
    private final NettyValues nettyValues; // Access point for shared Netty configurations
    private final ITxnManager<T> txnManager; // Utilized exclusively by the final terminal handler
    private final String telegramName;

    public ClientHandlerContext(String flowName, Upstream upstream, Object values,
                                NettyValues nettyValues, ITxnManager<T> txnManager, String telegramName) {
        this.flowName = flowName;
        this.upstream = upstream;
        this.values = values;
        this.nettyValues = nettyValues;
        this.txnManager = txnManager;
        this.telegramName = telegramName;
    }
}
