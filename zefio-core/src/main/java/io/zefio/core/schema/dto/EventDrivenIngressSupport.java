package io.zefio.core.schema.dto;

/**
 * Interface for Ingress modules that support multiple parallel consumers.
 * Typically used by event-driven sources like Message Queues or Kafka.
 */
public interface EventDrivenIngressSupport {
    /**
     * Returns the number of concurrent consumer threads to be allocated.
     */
    Integer getNumOfConsumer();
}
