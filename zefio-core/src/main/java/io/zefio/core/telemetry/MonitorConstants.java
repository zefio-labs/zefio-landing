package io.zefio.core.telemetry;

/**
 * Central repository for telemetry metric names.
 * Defines standard keys used for reporting to external monitoring systems.
 */
public class MonitorConstants {
    private MonitorConstants() {}

    // Module Metrics
    public static final String MODULE_ACCEPTED = "module_accepted_total";
    public static final String MODULE_FAILED = "module_failed_total";
    public static final String MODULE_EXEC_AVG = "module_exec_avg_ms";
    public static final String MODULE_EXEC_MAX = "module_exec_max_ms";
    public static final String MODULE_TPS = "module_tps";
    public static final String MODULE_ALERT_FAILURE_RATE = "module_alert_failure_rate";
    public static final String MODULE_ALERT_SLOW_AVG = "module_alert_slow_avg";

    // JVM Metrics
    public static final String JVM_HEAP_USAGE_RATIO = "jvm_heap_usage_ratio";
    public static final String JVM_FULL_GC_PERIOD_COUNT = "jvm_full_gc_period_count";
    public static final String JVM_FULL_GC_PERIOD_AVG = "jvm_full_gc_period_avg_ms";
    public static final String JVM_HEAP_USAGE_BYTES = "jvm_heap_used_bytes";
    public static final String JVM_HEAP_MAX_BYTES = "jvm_heap_max_bytes";

    // Logging Metrics
    public static final String LOGGING_ERRORS_TOTAL = "logging_errors_total";
    public static final String LOGGING_ASYNC_QUEUE_RATIO = "logging_async_queue_ratio";
    public static final String LOGGING_ERRORS_PERIOD_COUNT = "logging_errors_period_count";
    public static final String LOGGING_ERROR_DETAIL = "logging_error_detail_total";

    // ConnectionPool Metrics
    public static final String CONNECTION_POOL_ACTIVE = "connection_pool_active";
    public static final String CONNECTION_POOL_IDLE = "connection_pool_idle";
    public static final String CONNECTION_POOL_MAX = "connection_pool_max";
    public static final String CONNECTION_POOL_USAGE_RATIO = "connection_pool_usage_ratio";

    // NettyEventLoop Metrics
    public static final String NETTY_THREADS_TOTAL = "netty_threads_total";
    public static final String NETTY_THREADS_ACTIVE = "netty_threads_active";
    public static final String NETTY_PENDING_TASKS = "netty_pending_tasks";
    public static final String NETTY_CHANNELS_ACTIVE = "netty_channels_active";
    public static final String NETTY_DIRECT_MEMORY_BYTES = "netty_direct_memory_bytes";

    // Queue Metrics
    public static final String QUEUE_SIZE = "queue_size";
    public static final String QUEUE_USAGE_RATIO = "queue_usage_ratio";
    public static final String QUEUE_CAPACITY = "queue_capacity";

    // Thread Pool Metrics
    public static final String THREAD_POOL_ACTIVE_THREADS = "threadpool_active_threads";
    public static final String THREAD_POOL_SIZE = "threadpool_pool_size";
    public static final String THREAD_POOL_QUEUE_SIZE = "threadpool_queue_size";
    public static final String THREAD_POOL_CORE_USAGE_RATIO = "threadpool_core_usage_ratio";
    public static final String THREAD_POOL_MAX_USAGE_RATIO = "threadpool_max_usage_ratio";
    public static final String THREAD_POOL_THRESHOLD_QUEUE = "threadpool_threshold_queue";
}
