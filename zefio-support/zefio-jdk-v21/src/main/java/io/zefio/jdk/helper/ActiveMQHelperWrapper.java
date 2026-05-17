package io.zefio.jdk.helper;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

import jakarta.jms.ConnectionFactory;
import java.util.Map;

/**
 * ActiveMQ-specific implementation for managing JMS components.
 * Facilitates the creation of Upstream templates for sending and Ingress listeners for receiving.
 */
public class ActiveMQHelperWrapper extends AbstractJMSHelperWrapper {

    private static ActiveMQConnectionFactory createFactory(String brokerUrl,
                                                           String userName,
                                                           String password,
                                                           String clientId) {
        ActiveMQConnectionFactory mqQueueConnectionFactory = new ActiveMQConnectionFactory();
        mqQueueConnectionFactory.setBrokerURL(brokerUrl);
        mqQueueConnectionFactory.setUserName(userName);
        mqQueueConnectionFactory.setPassword(password);

        if (clientId != null && !clientId.isEmpty()) {
            mqQueueConnectionFactory.setClientID(clientId);
        }
        return mqQueueConnectionFactory;
    }

    /**
     * Creates a synchronous JmsTemplate for Upstream operations.
     */
    public static JmsTemplate createSyncTemplate(
            String brokerUrl,
            String userName,
            String password,
            String clientId,
            int maxConnections,
            String queueName,
            String topicName) {

        ConnectionFactory connectionFactory = createConnectionFactory(
                createFactory(brokerUrl, userName, password, clientId), userName, password, maxConnections
        );
        return getJmsTemplate(connectionFactory, queueName, topicName);
    }

    /**
     * Creates an asynchronous listener container for Ingress operations.
     */
    public static DefaultMessageListenerContainer createAsyncMessageListener(
            String brokerUrl,
            String userName,
            String password,
            String clientId,
            int maxConnections,
            String queueName,
            String topicName,
            JmsMessageHandler handler) throws Exception {

        ConnectionFactory connectionFactory = createConnectionFactory(
                createFactory(brokerUrl, userName, password, clientId), userName, password, maxConnections
        );

        return createMessageListener(connectionFactory, queueName, topicName, handler);
    }

    /**
     * Checks if the ActiveMQ broker is accessible using provided context parameters.
     */
    public static boolean isMQAvailable(Map<String, Object> context) {
        String brokerUrl = (String) context.get("brokerUrl");
        String password = (String) context.get("password");
        String userName = (String) context.get("userName");

        ActiveMQConnectionFactory mqQueueConnectionFactory = new ActiveMQConnectionFactory();
        mqQueueConnectionFactory.setBrokerURL(brokerUrl);
        mqQueueConnectionFactory.setPassword(password);
        mqQueueConnectionFactory.setUserName(userName);
        return mqAvailable(mqQueueConnectionFactory);
    }
}
