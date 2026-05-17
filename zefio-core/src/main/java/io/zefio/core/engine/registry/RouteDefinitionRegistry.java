package io.zefio.core.engine.registry;

import io.zefio.core.PipelineService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A centralized static registry used to store and manage active PipelineService (Flow) instances.
 * It provides thread-safe registration, lookup, and removal of flows based on their unique names.
 */
public class RouteDefinitionRegistry {
    private static final Map<String, PipelineService> flowMap = new ConcurrentHashMap<>();

    public static void register(String name, PipelineService flow) {
        flowMap.put(name, flow);
    }

    public static PipelineService getFlow(String name) {
        return flowMap.get(name);
    }

    public static void unregister(String name) {
        flowMap.remove(name);
    }

    /**
     * Removes all registered flows from the registry.
     * Primarily used for unit test initialization/cleanup or full system resets.
     */
    public static void clear() {
        flowMap.clear();
    }
}
