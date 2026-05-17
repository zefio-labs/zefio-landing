package io.zefio.gateway.netty.transaction;

import io.netty.channel.Channel;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.payload.Payload;

import java.util.concurrent.CompletableFuture;

/**
 * Manages transactions by binding them to the Netty Channel identity.
 * Ideal for protocols that do not support multiplexing.
 */
public class SessionTxnManager implements ITxnManager<Payload> {

	private final TxnManager<Payload> txnManager;

	public SessionTxnManager(String moduleName, long transactionTimeoutMillis, boolean isClientSend) {
		this.txnManager = new TxnManager<>(this, moduleName, transactionTimeoutMillis, isClientSend);
	}

	@Override
	public String getKey(Channel channel, Payload payload) {
		// Use Channel ID and Remote Address as the correlation key
		return channel.id().asLongText() + "_" + channel.remoteAddress().toString();
	}

	@Override
	public CompletableFuture<Payload> send(Channel channel, Payload payload) {
		return this.txnManager.sendAsync(channel, payload);
	}

	@Override
	public void complete(Channel channel, Payload payload) throws FlowException {
		this.txnManager.complete(channel, payload);
	}

	@Override
	public void close(Channel channel) {
		this.txnManager.close(channel);
	}

	@Override
	public void clear() {
		this.txnManager.clear();
	}
}
