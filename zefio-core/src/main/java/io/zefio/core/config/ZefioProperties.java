package io.zefio.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Type-safe configuration properties for Zefio Engine.
 * Binds properties prefixed with 'zefio' from application.yml.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "zefio")
public class ZefioProperties {

    private Node node = new Node();
    private Cp cp = new Cp();

    /**
     * Identifies the current Data Plane (DP) node in the cluster.
     */
    @Data
    public static class Node {
        private String id = "DP-01";
        private String group = "main";
    }

    /**
     * Configuration for Control Plane (CP) integration via Redis Hub.
     */
    @Data
    public static class Cp {
        private boolean enabled = true;
        private Redis redis = new Redis();
        private Metrics metrics = new Metrics();
    }

    @Data
    public static class Redis {
        private String url = "redis://e000.bond:6379/0";
    }

    @Data
    public static class Metrics {
        private long pushIntervalMs = 3000;
    }
}
