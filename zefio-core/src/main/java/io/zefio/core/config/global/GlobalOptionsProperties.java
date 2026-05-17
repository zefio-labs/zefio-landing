package io.zefio.core.config.global;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Global configuration properties for the engine's operational policies.
 * Includes default retry strategies, synchronization bridge capacities for Request-Reply,
 * and global exception handling behaviors.
 */
@Configuration
@ConfigurationProperties(prefix = "global-options")
@Getter
@Setter
public class GlobalOptionsProperties {
    private DefaultRetry defaultRetry = new DefaultRetry();
    private SyncBridge syncBridge = new SyncBridge();
    private ExceptionPolicyProperties exceptionPolicy = new ExceptionPolicyProperties();

    @Getter @Setter
    public static class DefaultRetry {
        private Boolean enabled = false;
        private Integer maxRetries = 3;
        private Integer delay = 100; // Delay between retries in milliseconds
    }

    @Getter @Setter
    public static class SyncBridge {
        /** Maximum number of concurrent Request-Reply correlations. */
        private int maxCapacity = 50000;
        /** Time-to-live for a correlation entry in the bridge. */
        private int expireSeconds = 60;
    }
}
