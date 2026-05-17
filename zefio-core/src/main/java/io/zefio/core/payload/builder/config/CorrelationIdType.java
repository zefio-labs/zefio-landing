package io.zefio.core.payload.builder.config;

/**
 * Enumeration of supported correlation identifier extraction types.
 */
public enum CorrelationIdType {
	JMSMessageID,
	JMSCorrelationID,
	Offset,
	Key,        // Deprecated: Use SpEL for better flexibility
	JsonPath,   // Deprecated: Use SpEL for better flexibility
	XPath,      // Deprecated: Use SpEL for better flexibility
	SpEL,       // Uses the unified expression engine
	None
}
