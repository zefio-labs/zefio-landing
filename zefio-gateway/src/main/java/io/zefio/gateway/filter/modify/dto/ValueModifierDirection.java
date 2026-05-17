package io.zefio.gateway.filter.modify.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Standardizes the direction of data transfer during payload modification.
 */
@JsonPropertyOrder(alphabetic = true)
public enum ValueModifierDirection {
    /** Injects metadata from payload headers into the message body. */
    PROPERTY_TO_BODY,
    /** Extracts specific data segments from the body into payload headers. */
    BODY_TO_PROPERTY,
    /** Directly mutates or replaces values within the message body. */
    MODIFY_BODY
}
