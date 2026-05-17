package io.zefio.core.config.flow;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Root configuration class that aggregates all flow-related settings.
 * It serves as the primary entry point for parsing the system's YAML/JSON specification,
 * including global profiles, reusable error templates, and individual flow definitions.
 */
@Configuration
@ConfigurationProperties(prefix = "")
@Data
public class FlowSettings {

    /** Specification for split configuration files to be imported. */
    private List<String> imports = new ArrayList<>();

    /** Common profiles used for property inheritance and template sharing. */
    private Map<String, Object> profiles = new HashMap<>();

    /** Global error handling templates that can be referenced across multiple flows. */
    @JsonProperty("global-errors")
    private Map<String, StepConfiguration> globalErrors = new HashMap<>();

    /** Global telegram definitions for data format parsing. */
    private Map<String, TelegramsConfiguration> telegrams = new HashMap<>();

    /** The collection of executable flow definitions. */
    private List<FlowConfiguration> flows = new ArrayList<>();

    /** Global registry for endpoint metadata (e.g., reusable Upstream definitions). */
    private List<StepConfiguration> endpoints;
}
