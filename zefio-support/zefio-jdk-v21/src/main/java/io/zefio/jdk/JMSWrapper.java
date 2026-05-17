package io.zefio.jdk;

import com.ibm.msg.client.jakarta.wmq.WMQConstants;
import jakarta.jms.*;
import io.zefio.jdk.helper.JmsMQType;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.util.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility for abstracting JMS message operations.
 * Handles payload extraction, header management, and Request-Response patterns
 * for both Ingress and Upstream flows.
 */
public class JMSWrapper {
	private final static Logger log = LoggerFactory.getLogger(JMSWrapper.class);

	/**
	 * Extracts raw bytes from various JMS message types with specific encoding.
	 */
	public static byte[] getJMSMessage(Object object, Charset encoding) throws Exception {
		if (object instanceof TextMessage && !ObjectUtils.isEmpty(encoding)) {
			TextMessage message = (TextMessage) object;
			return message.getText().getBytes(encoding);
		}
		return getJMSMessage(object);
	}

	/**
	 * Extracts raw bytes from various JMS message types.
	 * Supports Text, Bytes, Stream, Map, and Object messages.
	 */
	public static byte[] getJMSMessage(Object object) throws Exception {
		try {
			if (object instanceof TextMessage) {
				TextMessage message = (TextMessage) object;
				// Handle IBM MQ specific character set property if present
				String encoding = message.getStringProperty(WMQConstants.JMS_IBM_CHARACTER_SET);
				if (ObjectUtils.isEmpty(encoding)) {
					return message.getText().getBytes();
				} else {
					return message.getText().getBytes(Charset.forName(encoding));
				}
			} else if (object instanceof BytesMessage) {
				BytesMessage message = (BytesMessage) object;
				byte[] datas = new byte[(int) message.getBodyLength()];
				message.readBytes(datas);
				return datas;
			} else if (object instanceof StreamMessage) {
				StreamMessage message = (StreamMessage) object;
				final ByteArrayOutputStream baos = new ByteArrayOutputStream();
				byte[] byteBuffer = new byte[4096];
				int byteCount;
				while ((byteCount = message.readBytes(byteBuffer)) != -1) {
					baos.write(byteBuffer, 0, byteCount);
				}
				baos.close();
				return baos.toByteArray();
			} else if (object instanceof MapMessage) {
				MapMessage message = (MapMessage) object;
				Map<String, Object> map = new HashMap<>();
				@SuppressWarnings("unchecked")
				Enumeration<String> en = message.getMapNames();
				while (en.hasMoreElements()) {
					String key = en.nextElement();
					map.put(key, message.getObject(key));
				}
				return serializeObject(map);
			} else if (object instanceof ObjectMessage) {
				ObjectMessage message = (ObjectMessage) object;
				return serializeObject(message.getObject());
			}
		} catch (JMSException e) {
			log.error("JMS Error code: <{}>, message: [{}]", e.getErrorCode(), e.getMessage());
			throw new Exception(e);
		}
		return null;
	}

	private static byte[] serializeObject(Object obj) {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
			 ObjectOutputStream oos = new ObjectOutputStream(baos)) {
			oos.writeObject(obj);
			return baos.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException("Serialization failed", e);
		}
	}

	public static String getJMSCorrelationID(Object obj) throws Exception {
		try {
			return ((Message) obj).getJMSCorrelationID();
		} catch (JMSException e) {
			log.error("Failed to get JMSCorrelationID: {}", e.getMessage());
			throw new Exception(e);
		}
	}

	public static String getJMSMessageID(Object obj) throws Exception {
		try {
			return ((Message) obj).getJMSMessageID();
		} catch (JMSException e) {
			log.error("Failed to get JMSMessageID: {}", e.getMessage());
			throw new Exception(e);
		}
	}

	public static boolean existJMSReplyTo(Object rawMessage) throws JMSException {
		return ((Message) rawMessage).getJMSReplyTo() != null;
	}

	/**
	 * Sends a response message back to the sender in an Ingress flow.
	 */
	public static void responseMessage(String logHeader, JmsMQType correlationIdType, byte[] datas, String trxId, Object rawMessage, JmsTemplate replyToJmsTemplate) throws JMSException {
		Message message = (Message) rawMessage;
		Destination replyToDestination = message.getJMSReplyTo();

		MessageCreator messageCreator = session -> {
			BytesMessage bytesMessage = session.createBytesMessage();
			bytesMessage.writeBytes(datas);

			if (correlationIdType == JmsMQType.CorrelationID) {
				bytesMessage.setJMSCorrelationID(trxId);
			}

			Destination target = existJMSReplyTo(message) ? replyToDestination : replyToJmsTemplate.getDefaultDestination();
			log.info("{} Response to [{}] correlationID [{}]\n[{}]", logHeader, target, trxId, bytesMessage);

			return bytesMessage;
		};

		if (existJMSReplyTo(message)) {
			replyToJmsTemplate.send(replyToDestination, messageCreator);
		} else {
			replyToJmsTemplate.send(messageCreator);
		}
	}

	/**
	 * Executes a one-way request in an Upstream flow.
	 */
	public static Object request(byte[] datas, JmsTemplate requestJmsTemplate) throws Exception {
		final AtomicReference<Object> sendAtomic = new AtomicReference<>();

		MessageCreator messageCreator = session -> {
			BytesMessage bytesMessage = session.createBytesMessage();
			bytesMessage.writeBytes(datas);
			sendAtomic.set(bytesMessage);
			return bytesMessage;
		};

		requestJmsTemplate.send(messageCreator);
		return sendAtomic.get();
	}

	/**
	 * Executes a synchronous two-way Request-Response using a TemporaryQueue (Upstream).
	 */
	public static Object requestAndResponse(JmsMQType correlationIdType, byte[] datas, String trxId, JmsTemplate requestJmsTemplate) {
		log.info("No response queue configured. Using TemporaryQueue for two-way Upstream communication.");
		return requestJmsTemplate.execute(session -> {
			String destinationName = requestJmsTemplate.getDefaultDestinationName();
			Assert.notNull(destinationName, "Default destination must not be null");

			Destination requestQueue = session.createQueue(destinationName);
			TemporaryQueue replyQueue = session.createTemporaryQueue();

			try (MessageProducer producer = session.createProducer(requestQueue);
				 MessageConsumer consumer = session.createConsumer(replyQueue)) {

				BytesMessage requestMessage = session.createBytesMessage();
				requestMessage.writeBytes(datas);

				if (correlationIdType == JmsMQType.CorrelationID) {
					requestMessage.setJMSCorrelationID(trxId);
				}

				requestMessage.setJMSReplyTo(replyQueue);
				producer.send(requestMessage);

				log.info("Sent Upstream request to [{}]\n[{}]", requestQueue, requestMessage);

				Message responseMessage = consumer.receive(requestJmsTemplate.getReceiveTimeout());
				log.info("Received Upstream response from [{}]\n[{}]", replyQueue, responseMessage);

				return responseMessage;
			} finally {
				replyQueue.delete();
			}
		}, true);
	}

	/**
	 * Executes a synchronous two-way Request-Response using a designated response queue (Upstream).
	 */
	public static Pair<Object, String> requestAndResponse(JmsMQType correlationIdType, byte[] datas, JmsTemplate requestJmsTemplate, JmsTemplate responseJmsTemplate) throws Exception {
		Object requestMessage = request(datas, requestJmsTemplate);
		String trxId = "";

		switch (correlationIdType) {
			case JMSMessageID:
				trxId = getJMSMessageID(requestMessage);
				break;
			case JMSCorrelationID:
				trxId = getJMSCorrelationID(requestMessage);
				break;
			default:
				break;
		}

		log.info("Sent Upstream request to [{}] correlationID [{}]\n[{}]", requestJmsTemplate.getDefaultDestination(), trxId, requestMessage);

		String selector = "JMSCorrelationID = '" + trxId + "'";
		Message responseMessage = responseJmsTemplate.receiveSelected(selector);

		log.info("Received Upstream response from [{}] correlationID [{}]\n[{}]", responseJmsTemplate.getDefaultDestination(), trxId, responseMessage);
		return Pair.of(responseMessage, trxId);
	}
}
