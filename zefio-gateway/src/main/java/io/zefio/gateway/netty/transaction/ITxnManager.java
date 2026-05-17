package io.zefio.gateway.netty.transaction;

import io.netty.channel.Channel;
import io.zefio.core.common.exception.FlowException;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for managing the lifecycle of a network transaction.
 * Handles sending messages and correlating incoming responses to pending promises.
 *
 * @param <T> The type of message being managed (typically Payload)
 */
public interface ITxnManager<T> {

	/** Initiates an asynchronous send operation. */
	CompletableFuture<T> send(Channel channel, T body);

	/** Completes a pending transaction with the received response. */
	void complete(Channel channel, T newValue) throws FlowException;

	/** Generates or retrieves the unique correlation key for the transaction. */
	String getKey(Channel channel, T body);

	/** Closes associated resources for a specific channel. */
	void close(Channel channel);

	/** Clears all pending transactions (typically used during shutdown). */
	void clear();
}
