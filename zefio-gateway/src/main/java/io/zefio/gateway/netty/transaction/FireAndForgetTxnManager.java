package io.zefio.gateway.netty.transaction;

import io.netty.channel.Channel;
import io.zefio.core.payload.Payload;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation for Fire-and-Forget communication patterns.
 * The promise is completed immediately upon a successful socket write.
 */
public class FireAndForgetTxnManager implements ITxnManager<Payload> {

	public FireAndForgetTxnManager(String moduleName) {
	}

	@Override
	public String getKey(Channel channel, Payload payload) {
		return ""; // No correlation key needed for one-way traffic
	}

	@Override
	public CompletableFuture<Payload> send(Channel channel, Payload payload) {
		CompletableFuture<Payload> future = new CompletableFuture<>();

		channel.writeAndFlush(payload).addListener(f -> {
			if (f.isSuccess()) {
				// Complete the future immediately as no response is expected
				future.complete(null);
			} else {
				if (f.cause() instanceof IOException) {
					channel.close();
				}
				future.completeExceptionally(f.cause());
			}
		});
		return future;
	}

	@Override
	public void complete(Channel channel, Payload payload) {
		// No-op for Fire-and-Forget
	}

	@Override
	public void close(Channel channel) {
		channel.close();
	}

	@Override
	public void clear() {
	}
}
