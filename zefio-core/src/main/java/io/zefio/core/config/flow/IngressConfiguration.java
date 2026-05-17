package io.zefio.core.config.flow;

import io.zefio.core.payload.ExchangePattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for the entry point (Edge) of a flow.
 * Defines the transport protocol, format (Telegram), and initial interaction model.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class IngressConfiguration {
    private String name;
    private String label;
    private String type;
    private String clazz;
    private String profile;
    private String telegram;

    /** Component-specific parameters (e.g., listening port, queue name). */
    private Map<String, Object> config = new HashMap<>();

    /** Determines if the ingress should wait for a response or acknowledge immediately. */
    private ExchangePattern exchangePattern;
}
