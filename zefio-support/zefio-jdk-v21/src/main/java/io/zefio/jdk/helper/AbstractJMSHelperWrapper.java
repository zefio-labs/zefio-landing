package io.zefio.jdk.helper;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;

/**
 * Base wrapper for JMS infrastructure configuration.
 * Provides standard methods for creating Upstream templates and Ingress listeners.
 */
public abstract class AbstractJMSHelperWrapper {

	/**
	 * Configures a JmsTemplate for Upstream message production.
	 */
	protected static JmsTemplate getJmsTemplate(ConnectionFactory connectionFactory, String queue, String topic) {
		JmsTemplate jmsTemplate = new JmsTemplate(connectionFactory);
		if (ObjectUtils.isNotEmpty(queue)) {
			jmsTemplate.setDefaultDestinationName(queue);
		} else {
			jmsTemplate.setDefaultDestinationName(topic);
		}
		return jmsTemplate;
	}

	/**
	 * Creates a connection factory with credential adaptation and session caching.
	 */
	protected static ConnectionFactory createConnectionFactory(
			ConnectionFactory customConnectionFactory,
			String userName,
			String password,
			int maxConnections) {

		UserCredentialsConnectionFactoryAdapter connectionFactoryAdapter = new UserCredentialsConnectionFactoryAdapter();
		connectionFactoryAdapter.setTargetConnectionFactory(customConnectionFactory);
		connectionFactoryAdapter.setUsername(userName);
		connectionFactoryAdapter.setPassword(password);

		CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
		connectionFactory.setTargetConnectionFactory(connectionFactoryAdapter);
		connectionFactory.setSessionCacheSize(maxConnections);

		return connectionFactory;
	}

	/**
	 * Initializes a DefaultMessageListenerContainer for Ingress message consumption.
	 */
	public static DefaultMessageListenerContainer createMessageListener(
			ConnectionFactory customConnectionFactory,
			String queueName,
			String topicName,
			JmsMessageHandler handler) throws Exception {

		DefaultMessageListenerContainer container = new DefaultMessageListenerContainer();
		container.setConnectionFactory(customConnectionFactory);
		container.setDestinationName(ObjectUtils.firstNonNull(queueName, topicName));
		container.setMessageListener(new JdkReceiverAdapter(handler));
		container.setAutoStartup(true);
		container.setSessionTransacted(false);
		container.setAcceptMessagesWhileStopping(false);

		return container;
	}

	/**
	 * Validates the availability of the MQ server by attempting a test connection.
	 */
	public static boolean mqAvailable(ConnectionFactory connectionFactory) {
		try {
			try (Connection connection = connectionFactory.createConnection()) {
				connection.start();
			}
			return true;
		} catch (JMSException e) {
			return false;
		}
	}
}
