package io.zefio.core.telemetry.module;

import io.zefio.core.common.base.PluginType;
import io.zefio.core.common.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Aggregates performance metrics for individual modules using an asynchronous queue.
 * Tracks event counts and execution times while ensuring thread safety and overflow protection.
 */
public class ModuleMetricsAggregator {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final String monitoringType;
    private final String name;
    private final Map<String, AtomicLong> propertiesMap = new ConcurrentHashMap<>();

    private static final String SUFFIX_CHANNEL_ACTIVE   = ".channel.active";
    private static final String SUFFIX_CHANNEL_INACTIVE = ".channel.inactive";
    private static final String SUFFIX_EVENT_RECEIVED   = ".event.received";
    private static final String SUFFIX_EVENT_ACCEPTED   = ".event.accepted";
    private static final String SUFFIX_EVENT_FAILED     = ".event.failed";
    private static final String SUFFIX_EXEC_COUNT       = ".exec.count";
    private static final String SUFFIX_EXEC_TIME_TOTAL  = ".exec.time.total";
    private static final String SUFFIX_EXEC_TIME_MAX    = ".exec.time.max";
    private static final String SUFFIX_START_TIME       = ".start.time";
    private static final String SUFFIX_STOP_TIME        = ".stop.time";

    private final ConcurrentLinkedQueue<IncrementPayload> queue = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private static final int BATCH_INTERVAL_MS = 100;

    public ModuleMetricsAggregator(PluginType filterType, String name) {
        this.monitoringType = filterType.name();
        this.name = name;

        propertiesMap.put(key(SUFFIX_CHANNEL_ACTIVE), new AtomicLong(0));
        propertiesMap.put(key(SUFFIX_CHANNEL_INACTIVE), new AtomicLong(0));
        propertiesMap.put(key(SUFFIX_EVENT_RECEIVED), new AtomicLong(0));
        propertiesMap.put(key(SUFFIX_EVENT_ACCEPTED), new AtomicLong(0));
        propertiesMap.put(key(SUFFIX_EVENT_FAILED), new AtomicLong(0));
        propertiesMap.put(key(SUFFIX_EXEC_COUNT), new AtomicLong(0));
        propertiesMap.put(key(SUFFIX_EXEC_TIME_TOTAL), new AtomicLong(0));
        propertiesMap.put(key(SUFFIX_EXEC_TIME_MAX), new AtomicLong(0));
        propertiesMap.put(key(SUFFIX_START_TIME), new AtomicLong(0));
        propertiesMap.put(key(SUFFIX_STOP_TIME), new AtomicLong(0));

        executor.scheduleAtFixedRate(this::flush, BATCH_INTERVAL_MS, BATCH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private String key(String postfix) {
        return name + postfix;
    }

    private long get(String key) {
        AtomicLong val = propertiesMap.get(key);
        return val != null ? val.get() : 0L;
    }

    private void set(String key, long value) {
        propertiesMap.computeIfAbsent(key, k -> new AtomicLong(0)).set(value);
    }

    private void enqueue(String counter, long delta) {
        queue.add(new IncrementPayload(counter, delta));
    }

    private void safeAddAndGet(String key, long delta) {
        AtomicLong slot = propertiesMap.get(key);
        if (slot != null) {
            slot.updateAndGet(old -> {
                if (old == Long.MAX_VALUE) return Long.MAX_VALUE;
                long next = old + delta;
                if (next < 0) return Long.MAX_VALUE;
                return next;
            });
        }
    }

    private void flush() {
        IncrementPayload event;
        while ((event = queue.poll()) != null) {
            final String eventKey = event.key;
            final long eventDelta = event.delta;

            if (eventKey.endsWith(SUFFIX_EXEC_TIME_MAX)) {
                AtomicLong slot = propertiesMap.get(eventKey);
                if (slot != null) slot.updateAndGet(curr -> Math.max(curr, eventDelta));
            } else {
                safeAddAndGet(eventKey, eventDelta);
            }
        }
    }

    public void start() {
        set(key(SUFFIX_STOP_TIME), 0L);
        propertiesMap.keySet().forEach(k -> set(k, 0L));
        set(key(SUFFIX_START_TIME), System.currentTimeMillis());
        log.info("Module started. Type: {}, Name: {}", monitoringType, name);
    }

    public void stop() {
        long stopMs = System.currentTimeMillis();
        set(key(SUFFIX_STOP_TIME), stopMs);

        long startMs = get(key(SUFFIX_START_TIME));
        long execCount = get(key(SUFFIX_EXEC_COUNT));
        long failedCount = get(key(SUFFIX_EVENT_FAILED));
        double avgTime = getExecutionAvg();
        long maxTime = getExecutionMax();

        String startTimeStr = TimeUtils.formatLogTime(startMs);
        String stopTimeStr = TimeUtils.formatLogTime(stopMs);

        log.info("[Shutdown] Module: {} (Type: {}) | RunTime: {}~{} | Exec: {} | Fail: {} | Avg: {}ms | Max: {}ms",
                name, monitoringType, startTimeStr, stopTimeStr, execCount, failedCount, String.format("%.2f", avgTime), maxTime);
    }

    public void reset() {
        log.info("Resetting counters for module: {} (Type: {})", name, monitoringType);
        set(key(SUFFIX_STOP_TIME), 0L);
        propertiesMap.keySet().forEach(counter -> {
            if (!counter.endsWith(SUFFIX_START_TIME) && !counter.endsWith(SUFFIX_STOP_TIME)) {
                set(counter, 0L);
            }
        });
        set(key(SUFFIX_START_TIME), System.currentTimeMillis());
    }

    public void incrementChannelActiveCount()   { enqueue(key(SUFFIX_CHANNEL_ACTIVE), 1); }
    public void incrementChannelInActiveCount() { enqueue(key(SUFFIX_CHANNEL_INACTIVE), 1); }
    public void incrementPayloadReceivedCount()   { enqueue(key(SUFFIX_EVENT_RECEIVED), 1); }
    public void incrementPayloadAcceptedCount()   { enqueue(key(SUFFIX_EVENT_ACCEPTED), 1); }
    public void incrementPayloadFailedCount()     { enqueue(key(SUFFIX_EVENT_FAILED), 1); }

    public long getChannelActiveCount()   { return get(key(SUFFIX_CHANNEL_ACTIVE)); }
    public long getChannelInActiveCount() { return get(key(SUFFIX_CHANNEL_INACTIVE)); }
    public long getPayloadReceivedCount()   { return get(key(SUFFIX_EVENT_RECEIVED)); }
    public long getPayloadAcceptedCount()   { return get(key(SUFFIX_EVENT_ACCEPTED)); }
    public long getPayloadFailedCount()     { return get(key(SUFFIX_EVENT_FAILED)); }

    public long getStartTime() { return get(key(SUFFIX_START_TIME)); }
    public long getStopTime()  { return get(key(SUFFIX_STOP_TIME)); }
    public PluginType getFilterType() { return PluginType.valueOf(monitoringType); }

    public void addExecutionTime(long millis) {
        enqueue(key(SUFFIX_EXEC_COUNT), 1);
        enqueue(key(SUFFIX_EXEC_TIME_TOTAL), millis);
        enqueue(key(SUFFIX_EXEC_TIME_MAX), millis);
    }

    public double getExecutionAvg() {
        long count = get(key(SUFFIX_EXEC_COUNT));
        long total = get(key(SUFFIX_EXEC_TIME_TOTAL));
        return count == 0 ? 0 : (double) total / count;
    }

    public long getExecutionMax() {
        return get(key(SUFFIX_EXEC_TIME_MAX));
    }

    public void shutdown() {
        flush();
        executor.shutdown();
    }

    private static class IncrementPayload {
        final String key;
        final long delta;
        IncrementPayload(String counter, long delta) { this.key = counter; this.delta = delta; }
    }
}
