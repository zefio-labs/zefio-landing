package io.zefio.core.telemetry.jvm;

import com.sun.management.UnixOperatingSystemMXBean;
import io.micrometer.core.instrument.Gauge;
import io.zefio.core.config.monitor.MonitorProperties.JvmMonitorThreshold;
import io.zefio.core.telemetry.AbstractMonitorLogger;
import io.zefio.core.telemetry.MonitorConstants;
import io.zefio.core.telemetry.MonitorInitContext;
import lombok.AllArgsConstructor;

import java.lang.management.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitor logger for JVM resources and garbage collection behavior.
 * Tracks heap usage, Full GC frequency/latency, and File Descriptor counts
 * to ensure the runtime stability of the engine.
 */
public class JvmMonitorLogger extends AbstractMonitorLogger {

    private final MemoryMXBean memoryMXBean;
    private final List<GarbageCollectorMXBean> gcMXBeans;
    private final OperatingSystemMXBean osMXBean;

    private boolean highFdWarned = false;
    private boolean highHeapWarned = false;
    private final JvmMonitorThreshold threshold;

    private final AtomicLong prevFullGcCount = new AtomicLong(0);
    private final AtomicLong prevFullGcTime = new AtomicLong(0);

    private volatile long lastPeriodFullGcCount = 0;
    private volatile double lastPeriodFullGcAvgTimeMs = 0.0;

    /**
     * List of recognized Old Generation Garbage Collector names across various JVM versions.
     */
    private static final List<String> OLD_GEN_GC_NAMES = Collections.unmodifiableList(Arrays.asList(
            "PS MarkSweep",
            "ConcurrentMarkSweep",
            "MarkSweepCompact",
            "G1 Old Generation",
            "G1 Concurrent GC"
    ));

    public JvmMonitorLogger(MonitorInitContext monitorInitContext, JvmMonitorThreshold jvmThreshold) {
        super(monitorInitContext, jvmThreshold.getIntervalSeconds());
        this.threshold = jvmThreshold;
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.gcMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        this.osMXBean = ManagementFactory.getOperatingSystemMXBean();

        updateGcMetrics(true);
    }

    @Override
    protected void bindMetrics() {
        // Current JVM Heap usage ratio
        registerMeter(Gauge.builder(MonitorConstants.JVM_HEAP_USAGE_RATIO, this, JvmMonitorLogger::getHeapUsageRatio)
                .tags(this.commonTags)
                .description("Current JVM Heap usage ratio")
                .register(this.meterRegistry));

        // Full GC count within the current monitoring period
        registerMeter(Gauge.builder(MonitorConstants.JVM_FULL_GC_PERIOD_COUNT, this, logger -> logger.lastPeriodFullGcCount)
                .tags(this.commonTags)
                .register(this.meterRegistry));

        // Average Full GC time within the current monitoring period
        registerMeter(Gauge.builder(MonitorConstants.JVM_FULL_GC_PERIOD_AVG, this, logger -> logger.lastPeriodFullGcAvgTimeMs)
                .tags(this.commonTags)
                .register(this.meterRegistry));

        // Absolute heap usage in bytes
        registerMeter(Gauge.builder(MonitorConstants.JVM_HEAP_USAGE_BYTES, this, logger -> logger.memoryMXBean.getHeapMemoryUsage().getUsed())
                .tags(this.commonTags)
                .baseUnit("bytes")
                .register(this.meterRegistry));

        // Configured maximum heap size in bytes
        registerMeter(Gauge.builder(MonitorConstants.JVM_HEAP_MAX_BYTES, this, logger -> logger.memoryMXBean.getHeapMemoryUsage().getMax())
                .tags(this.commonTags)
                .baseUnit("bytes")
                .register(this.meterRegistry));
    }

    private double getHeapUsageRatio() {
        if (memoryMXBean == null) return 0.0;
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        return (double) heapUsage.getUsed() / heapUsage.getMax();
    }

    private GcMetrics getOldGenGcMetrics() {
        long currentCount = 0;
        long currentTime = 0;

        for (GarbageCollectorMXBean bean : gcMXBeans) {
            if (OLD_GEN_GC_NAMES.contains(bean.getName())) {
                currentCount += bean.getCollectionCount();
                currentTime += bean.getCollectionTime();
            }
        }
        return new GcMetrics(currentCount, currentTime);
    }

    private void updateGcMetrics(boolean initial) {
        GcMetrics currentMetrics = getOldGenGcMetrics();
        prevFullGcCount.set(currentMetrics.count);
        prevFullGcTime.set(currentMetrics.time);
    }

    @Override
    protected String getMonitorPrefix() {
        return "JVMMetrics";
    }

    @Override
    protected String createInfoLogMessage() {
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        long used = heapUsage.getUsed();
        long max = heapUsage.getMax();
        double usedRatio = (double) used / max;

        GcMetrics currentMetrics = getOldGenGcMetrics();
        this.lastPeriodFullGcCount = currentMetrics.count - prevFullGcCount.get();
        long deltaTimeMs = currentMetrics.time - prevFullGcTime.get();
        this.lastPeriodFullGcAvgTimeMs = (lastPeriodFullGcCount > 0) ? (double) deltaTimeMs / lastPeriodFullGcCount : 0.0;

        return String.format("HeapUsed=%s MaxHeap=%s Usage=%.2f%% | FullGC(Count)=%d (AvgTime=%.2fms)",
                formatBytes(used), formatBytes(max), usedRatio * 100, lastPeriodFullGcCount, lastPeriodFullGcAvgTimeMs);
    }

    private String formatBytes(long bytes) {
        return String.format("%.2fMB", bytes / (1024.0 * 1024.0));
    }

    @Override
    protected void checkAndPrintWarnings() {
        checkHeapUsageWarning();
        checkGcWarnings();
        checkFileDescriptorWarning();

        updateGcMetrics(false);
    }

    private void checkHeapUsageWarning() {
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        double currentRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        final double heapThreshold = this.threshold.getHeapUsageRatio();

        if (currentRatio > heapThreshold) {
            if (!highHeapWarned) {
                log.warn("{} WARN: High Heap usage detected: {}% > {}%",
                        getLogTag(), String.format("%.2f", currentRatio * 100), heapThreshold * 100);
                highHeapWarned = true;
            }
        } else if (highHeapWarned) {
            log.info("{} RECOVERY: Heap usage returned to normal: {}%",
                    getLogTag(), String.format("%.2f", currentRatio * 100));
            highHeapWarned = false;
        }
    }

    private void checkGcWarnings() {
        final long oldGenGcTimeThreshold = this.threshold.getOldGenGcTimeMs();
        final long gcFrequencyThresholdSec = this.threshold.getGcFrequencySec();
        final long intervalMs = threshold.getIntervalSeconds() * 1000L;

        GcMetrics currentMetrics = getOldGenGcMetrics();
        long deltaCount = currentMetrics.count - prevFullGcCount.get();
        long deltaTimeMs = currentMetrics.time - prevFullGcTime.get();

        double avgGcTimeMs = (deltaCount > 0) ? (double) deltaTimeMs / deltaCount : 0.0;

        // Check for high Old Generation GC latency
        if (deltaCount > 0 && avgGcTimeMs > oldGenGcTimeThreshold) {
            log.warn("{} WARN: High Old Gen GC Avg Time: Avg {}ms > {}ms (Total Count: {})",
                    getLogTag(), String.format("%.2f", avgGcTimeMs), oldGenGcTimeThreshold, deltaCount);
        }

        // Calculate Full GC frequency: $Frequency = deltaCount / (intervalMs / 1000.0)$
        double frequencySec = (double) deltaCount / (intervalMs / 1000.0);

        // Threshold calculation: $Threshold = 1.0 / gcFrequencyThresholdSec$
        double frequencyThreshold = 1.0 / gcFrequencyThresholdSec;

        if (deltaCount > 0 && frequencySec > frequencyThreshold) {
            log.warn("{} WARN: High Full GC Frequency: {} times/sec > {}/sec (Threshold: 1/{} sec)",
                    getLogTag(), String.format("%.4f", frequencySec), String.format("%.4f", frequencyThreshold), gcFrequencyThresholdSec);
        }
    }

    private void checkFileDescriptorWarning() {
        if (osMXBean instanceof UnixOperatingSystemMXBean) {
            UnixOperatingSystemMXBean unixOsBean = (UnixOperatingSystemMXBean) osMXBean;
            long openFd = unixOsBean.getOpenFileDescriptorCount();
            long maxFd = unixOsBean.getMaxFileDescriptorCount();

            if (maxFd > 0) {
                double fdRatio = (double) openFd / maxFd;
                if (fdRatio > 0.85) {
                    if (!highFdWarned) {
                        log.warn("{} WARN: High File Descriptor usage: {} / {} ({}%)",
                                getLogTag(), openFd, maxFd, String.format("%.2f", fdRatio * 100));
                        highFdWarned = true;
                    }
                } else if (highFdWarned) {
                    log.info("{} RECOVERY: File Descriptor usage returned to normal.", getLogTag());
                    highFdWarned = false;
                }
            }
        }
    }

    @Override
    public void reset() {
        log.info("{} Resetting internal JVM alert states and GC baseline.", getLogTag());
        this.highHeapWarned = false;
        this.highFdWarned = false;
        this.lastPeriodFullGcCount = 0;
        this.lastPeriodFullGcAvgTimeMs = 0.0;
        updateGcMetrics(true);
    }

    @AllArgsConstructor
    private static class GcMetrics {
        final long count;
        final long time;
    }
}
