package io.zefio.jdk.helper;

/**
 * Enumeration of JMS message identification and correlation strategies.
 * Used to define tracking behavior across Ingress and Upstream flows.
 */
public enum JmsMQType {
	/** Identification based on the native JMSMessageID. */
	JMSMessageID,

	/** Correlation based on the standard JMSCorrelationID header. */
	JMSCorrelationID,

	/** Custom correlation identifier mapping. */
	CorrelationID,

	/** No specific identification strategy applied. */
	None
}
