package io.zefio.gateway.error.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Configuration for fault handling in XML message formats.
 * Inherits key-based logic from JsonFaultValues for path-based manipulation.
 */
@JsonPropertyOrder(alphabetic = true)
@EqualsAndHashCode(callSuper = true)
@Data
public class XmlFaultValues extends JsonFaultValues {
    // Inherits all fields and logic from JsonFaultValues for XML path manipulation.
}
