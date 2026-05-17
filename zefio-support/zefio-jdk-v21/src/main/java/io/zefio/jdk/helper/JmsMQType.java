package io.zefio.jdk.helper;

/**
 * Enumeration of JMS message identification and correlation strategies.
 * Used to define how messages are selected or tracked across Ingress and Upstream flows.
 */
public enum JmsMQType {
	/** Correlation based on the standard JMSMessageID header. */
	JMSMessageID,

	/** Correlation based on the standard JMSCorrelationID header. */
	JMSCorrelationID,

	/** Custom correlation ID field mapping. */
	CorrelationID,

	/** No specific correlation or selection strategy applied. */
	None
}
