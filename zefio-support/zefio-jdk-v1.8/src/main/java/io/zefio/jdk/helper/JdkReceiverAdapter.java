package io.zefio.jdk.helper;

import javax.jms.Message;
import javax.jms.MessageListener;

/**
 * Adapter class that bridges standard JMS MessageListener with
 * custom Zefio JmsMessageHandler.
 */
public class JdkReceiverAdapter implements MessageListener {
	private final JmsMessageHandler handler;

	public JdkReceiverAdapter(JmsMessageHandler handler) {
		this.handler = handler;
	}

	/**
	 * Invoked by the JMS container upon message reception.
	 * Delegates processing to the internal handler.
	 */
	@Override
	public void onMessage(Message message) {
		handler.handle(message);
	}
}
