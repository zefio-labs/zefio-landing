package io.zefio.core.telemetry.registry;

import io.zefio.core.telemetry.provider.IMetricsResettable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing components that support metric reset operations.
 * Uses a thread-safe set to track active resettable instances.
 */
public class MonitorRegistry {
    private static final Set<IMetricsResettable> resettables = ConcurrentHashMap.newKeySet();

    public static void register(IMetricsResettable component) { resettables.add(component); }
    public static void unregister(IMetricsResettable component) { resettables.remove(component); }
    public static Set<IMetricsResettable> getAll() { return resettables; }
}
