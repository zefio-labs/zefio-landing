package io.zefio.core.payload;

/**
 * Defines the messaging interaction model.
 */
public enum ExchangePattern {
	/** One-way messaging; the client does not expect a business response. */
	FireAndForget,

	/** Two-way messaging; the client waits for a synchronous or asynchronous response. */
	RequestReply
}
