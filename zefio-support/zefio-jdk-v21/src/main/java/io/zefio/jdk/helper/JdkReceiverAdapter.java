package io.zefio.jdk.helper;

import jakarta.jms.Message;
import jakarta.jms.MessageListener;

/**
 * Adapter class that bridges the Jakarta JMS MessageListener with
 * the custom Zefio JmsMessageHandler.
 */
public class JdkReceiverAdapter implements MessageListener {
	private final JmsMessageHandler handler;

	public JdkReceiverAdapter(JmsMessageHandler handler) {
		this.handler = handler;
	}

	/**
	 * Invoked by the JMS container when a message is received.
	 * Delegates the message to the internal handler.
	 */
	@Override
	public void onMessage(Message message) {
		handler.handle(message);
	}
}
