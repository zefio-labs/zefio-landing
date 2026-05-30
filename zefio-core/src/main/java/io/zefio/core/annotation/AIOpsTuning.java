package io.zefio.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation defining safety guards and constraints for the Control Plane (AIOps)
 * when dynamically tuning property values (Hot Deploy) at runtime.
 */
@Target({ FIELD })
@Retention(RUNTIME)
@Documented
public @interface AIOpsTuning {

    /**
     * Indicates whether zero-downtime hot deployment is allowed.
     * If false, the AI must not generate patches to change this value.
     */
    boolean hotDeployable() default true;

    /**
     * Indicates whether changing this value requires an engine or pipeline restart.
     */
    boolean restartRequired() default false;

    /**
     * Minimum tunable value (for numeric fields).
     * Parsed from String since Java annotations do not allow nulls.
     */
    String min() default "";

    /**
     * Maximum tunable value (for numeric fields).
     */
    String max() default "";

    /**
     * Regular expression pattern used by the Control Plane to validate string fields
     * (e.g., cron expressions, network IP masks, path templates) prior to deployment.
     */
    String pattern() default "";

    /**
     * Risk level of the change impacting the system. Used for AI prompt constraints.
     */
    RiskLevel riskLevel() default RiskLevel.LOW;

    /**
     * Category of the tuning property.
     */
    Category category() default Category.GENERAL;

    // ========================================================================
    // Internal Enum Definitions
    // ========================================================================
    enum RiskLevel {
        LOW,        // Simple value change, non-critical to the system (e.g., logging level)
        MEDIUM,     // Impacts performance (e.g., timeouts, connection pool adjustments)
        HIGH,       // Risk of structural change or resource exhaustion (e.g., massive thread increases)
        CRITICAL    // Risk of system downtime, such as protocol spec changes (e.g., port changes)
    }

    enum Category {
        NETWORK_TIMEOUT,  // Communication delay related (readTimeout, connectTimeout)
        RESOURCE_SCALE,   // System resource related (workThreadCount, poolMaxSize)
        PROTOCOL_SPEC,    // Message and protocol specs (statusOffset, pageLen - tuning prohibited)
        BUSINESS_LOGIC,   // Business logic control (e.g., retry counts)
        SECURITY,
        GENERAL
    }
}
