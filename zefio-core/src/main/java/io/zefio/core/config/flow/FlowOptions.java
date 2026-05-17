package io.zefio.core.config.flow;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.*;

/**
 * Encapsulates performance-related tuning parameters for the flow executor.
 * Manages thread pool behavior and SEDA queue capacities to ensure resource isolation.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@JsonPropertyOrder(alphabetic = true)
@Data
public class FlowOptions {

    private ThreadPoolOptions threadPool = new ThreadPoolOptions();
    private CPUQueueOptions cpuQueue = new CPUQueueOptions();
    private IOQueueOptions ioQueue = new IOQueueOptions();

    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @Setter
    public static class ThreadPoolOptions {
        private int corePoolSize = 50;
        private int maxPoolSize = 100;
        private int queueCapacity = 2000;
        private AutoScalingOptions autoScaling = new AutoScalingOptions();
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class AutoScalingOptions {
        private boolean enabled = false;
        private double threshold = 0.5;
        private int checkInterval = 5;
        private int scaleUpStep = 2;
        private int scaleDownStep = 1;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class CPUQueueOptions {
        private int capacity = 10000;

        public void setCapacity(int capacity) {
            // Defensive check to maintain default value if an invalid capacity is provided.
            if (capacity > 0) {
                this.capacity = capacity;
            }
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class IOQueueOptions {
        private int capacity = 5000;

        public void setCapacity(int capacity) {
            if (capacity > 0) {
                this.capacity = capacity;
            }
        }
    }
}
