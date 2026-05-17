package io.zefio.core.payload;

import java.nio.charset.Charset;
import java.util.Date;
import java.util.Map;

/**
 * The core data interface for all messages within the engine.
 * It manages the lifecycle of the data, including lazy parsing,
 * transcoding, metadata headers, and execution metrics.
 */
public interface Payload {

	// --- Lifecycle & Cloning ---
	/** Creates a deep copy of the payload, typically for parallel execution branches or retries. */
	Payload copyFactory(Payload payload);

	/** Merges data from a response payload back into this instance (used in Scatter-Gather). */
	void mergeResponse(Payload responsePayload, boolean includeMetrics);

	// --- Data & Body Management ---
	byte[] getBody();
	void setBody(byte[] body);

	/** Provides a logical Map view of the body for SpEL evaluation (Lazy Parsing). */
	Map<String, Object> getBodyMap();
	void setBodyMap(Map<String, Object> map);

	/** Stores the original raw request body for auditing or protocol-specific needs. */
	byte[] getRequestBody();
	void setRequestBody(byte[] requestBody);

	/** Flag indicating if the body has been altered by a transformer or processor. */
	boolean isBodyModified();
	void setBodyModified(boolean bodyModified);

	// --- Identification & Tracing ---
	String getTrxID();
	void setTrxID(String trxID);

	String getTelegramName();
	void setTelegramName(String telegramName);

	// --- Metadata (Headers) ---
	void setHeader(String key, Object value);
	void setHeader(Map<String, Object> map);
	Object getHeader(String key);
	Map<String, Object> getHeaders();
	boolean containsKeyHeader(String key);

	/** Retrieves a subset of headers based on a specific key prefix. */
	Map<String, Object> getSubHeaders(String prefix);
	void removeHeadersByPrefix(String prefix);

	// --- Context & Callbacks ---
	/** The MDC context used for correlated logging across asynchronous stages. */
	Map<String, String> getMdcContext( );
	void setMdcContext(Map<String, String> mdcContext);

	/** The listener used to return the final response to the Ingress Edge. */
	ResponseListener getCallback();
	void setCallback(ResponseListener callback);

	Charset getCurrentEncoding();
	void setCurrentEncoding(Charset currentEncoding);

	// --- Exception Handling ---
	void setThrowable(Throwable throwable);
	Throwable getThrowable();
	boolean hasException();

	// --- Telemetry & Metrics ---
	long getStartTime();
	void setStartTime(long time);

	Date getRequestTime();
	void setRequestTime(Date requestTime);

	Date getElapsedTime();
	void setElapsedTime(Date time);

	/** Tracks total time spent waiting in SEDA queues. */
	long getQueueWaitTime();
	void setQueueWaitTime(long duration);
	void addQueueWaitTime(long duration);

	/** Tracks total time spent communicating with Upstream (remote) systems. */
	long getRemoteTime();
	void setRemoteTime(long duration);
	void addRemoteTime(long duration);

	boolean isSuppressStatLog();
	void setSuppressStatLog(boolean suppressStatLog);

	/** Returns a string representation of the payload status/body for logging. */
	String response();
}
