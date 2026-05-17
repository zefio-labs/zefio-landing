package io.zefio.core.config.system;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Root system-level configuration properties.
 * Manages core framework behaviors such as hot-deployment toggles
 * and global shared resource pools.
 */
@Configuration
@ConfigurationProperties(prefix = "system")
@Getter
@Setter
public class SystemProperties {

    /** Flag to enable or disable dynamic flow reloading without system restart. */
    private boolean hotdeploy = false;

    /** Configuration for globally shared thread pools used across all flows. */
    private SharedThreadPoolProperties threadPools = new SharedThreadPoolProperties();
}
