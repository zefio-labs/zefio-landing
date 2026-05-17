package io.zefio.jdk.helper;

import com.ibm.mq.jakarta.jms.MQConnectionFactory;
import com.ibm.mq.jakarta.jms.MQQueueConnectionFactory;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import javax.net.ssl.SSLSocketFactory;
import java.util.Map;

/**
 * Helper wrapper for IBM MQ (Jakarta JMS).
 * Manages SSL/TLS configurations and provides infrastructure for
 * Ingress message consumption and Upstream message production.
 */
public class IbmMQHelperWrapper extends AbstractJMSHelperWrapper {

    /**
     * Initializes the MQ Connection Factory with enterprise security settings.
     * Sets specific system properties for TLS preference and Cipher mappings required by IBM MQ.
     */
    private static MQQueueConnectionFactory createFactory(String host,
                                                          String qManager,
                                                          int port,
                                                          String channel,
                                                          String appName,
                                                          int transportType,
                                                          int ccsid,
                                                          boolean sslEnable,
                                                          boolean sslFlipRequired,
                                                          String sslCipherSuit,
                                                          SSLSocketFactory sslSocketFactory) throws JMSException {
        // Force TLS and ECC Cipher support for modern security standards
        System.setProperty("com.ibm.mq.cfg.preferTLS", "true");
        System.setProperty("com.ibm.websphere.ssl.include.ECCiphers", "true");
        System.setProperty("com.ibm.mq.cfg.useIBMCipherMappings", "false");

        MQQueueConnectionFactory mqQueueConnectionFactory = new MQQueueConnectionFactory();
        mqQueueConnectionFactory.setHostName(host);
        mqQueueConnectionFactory.setQueueManager(qManager);
        mqQueueConnectionFactory.setPort(port);
        mqQueueConnectionFactory.setChannel(channel);
        mqQueueConnectionFactory.setAppName(appName);
        mqQueueConnectionFactory.setTransportType(transportType);
        mqQueueConnectionFactory.setCCSID(ccsid);

        if (ObjectUtils.isNotEmpty(sslEnable) && sslEnable) {
            mqQueueConnectionFactory.setSSLFipsRequired(sslFlipRequired);
            mqQueueConnectionFactory.setSSLCipherSuite(sslCipherSuit);
            mqQueueConnectionFactory.setSSLSocketFactory(sslSocketFactory);
        }
        return mqQueueConnectionFactory;
    }

    /**
     * Creates a JmsTemplate for synchronous Upstream message production.
     */
    public static JmsTemplate createSyncTemplate(
            String host,
            String qManager,
            int port,
            String channel,
            String appName,
            int transportType,
            int ccsid,
            String queue,
            String topic,
            String userName,
            String password,
            int maxConnections,
            boolean sslEnable,
            boolean sslFlipRequired,
            String sslCipherSuit,
            SSLSocketFactory sslSocketFactory) throws Exception {

        ConnectionFactory connectionFactory = createConnectionFactory(
                createFactory(host, qManager, port, channel, appName, transportType, ccsid, sslEnable, sslFlipRequired, sslCipherSuit, sslSocketFactory),
                userName, password, maxConnections
        );
        return getJmsTemplate(connectionFactory, queue, topic);
    }

    /**
     * Initializes a message listener container for asynchronous Ingress operations.
     */
    public static DefaultMessageListenerContainer createAsyncMessageListener(
            String host,
            String qManager,
            int port,
            String channel,
            String appName,
            int transportType,
            int ccsid,
            String userName,
            String password,
            int maxConnections,
            boolean sslEnable,
            boolean sslFlipRequired,
            String sslCipherSuit,
            SSLSocketFactory sslSocketFactory,
            String queueName,
            String topicName,
            JmsMessageHandler handler) throws Exception {

        ConnectionFactory connectionFactory = createConnectionFactory(
                createFactory(host, qManager, port, channel, appName, transportType, ccsid, sslEnable, sslFlipRequired, sslCipherSuit, sslSocketFactory),
                userName, password, maxConnections
        );

        return createMessageListener(connectionFactory, queueName, topicName, handler);
    }

    /**
     * Checks MQ server connectivity using provided context parameters.
     */
    public static boolean isMQAvailable(Map<String, Object> context) {
        String host = (String) context.get("host");
        int port = (int) context.get("port");
        String queueManager = (String) context.get("queueManager");
        String channel = (String) context.get("channel");
        int ccsid = (int) context.get("ccsid");

        MQConnectionFactory mqQueueConnectionFactory = new MQConnectionFactory();
        try {
            mqQueueConnectionFactory.setHostName(host);
            mqQueueConnectionFactory.setPort(port);
            mqQueueConnectionFactory.setQueueManager(queueManager);
            mqQueueConnectionFactory.setChannel(channel);
            mqQueueConnectionFactory.setTransportType(1); // Client (TCP)
            mqQueueConnectionFactory.setCCSID(ccsid);

            return mqAvailable(mqQueueConnectionFactory);
        } catch (Exception e) {
            return false;
        }
    }
}
