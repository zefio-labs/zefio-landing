package io.zefio.jdk.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static registry for managing and retrieving core engine components.
 * Provides a bypass mechanism for accessing Ingress and Upstream instances
 * outside the Spring application context.
 */
public class ComponentRegistry {
    private static final Map<String, Object> INGRESS = new ConcurrentHashMap<>();
    private static final Map<String, Object> INTERCEPTOR = new ConcurrentHashMap<>();
    private static final Map<String, Object> UPSTREAM = new ConcurrentHashMap<>();
    private static final Map<String, Object> ERRORS = new ConcurrentHashMap<>();

    public static void registerIngress(String name, Object obj) {
        INGRESS.put(name, obj);
    }
    public static void registerUpstream(String name, Object obj) {
        UPSTREAM.put(name, obj);
    }
    public static void registerInterceptor(String name, Object obj) {
        INTERCEPTOR.put(name, obj);
    }
    public static void registerError(String name, Object obj) {
        ERRORS.put(name, obj);
    }

    public static Object getIngress(String name) { return INGRESS.get(name); }
    public static Object getUpstream(String name) { return UPSTREAM.get(name); }
    public static Object getInterceptor(String name) { return INTERCEPTOR.get(name); }
    public static Object getError(String name) { return ERRORS.get(name); }

    public static void clear() {
        INGRESS.clear();
        UPSTREAM.clear();
        INTERCEPTOR.clear();
        ERRORS.clear();
    }

    /**
     * Returns a formatted summary of all registered components for diagnostic purposes.
     */
    public static String dumpAll() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== [Component Registry Dump] ==========\n");

        appendMap(sb, "INGRESS", INGRESS);
        appendMap(sb, "INTERCEPTOR", INTERCEPTOR);
        appendMap(sb, "UPSTREAM", UPSTREAM);
        appendMap(sb, "ERRORS", ERRORS);

        sb.append("===============================================");
        return sb.toString();
    }

    private static void appendMap(StringBuilder sb, String title, Map<String, Object> map) {
        sb.append(String.format("[%s] (%d entries)\n", title, map.size()));
        if (map.isEmpty()) {
            sb.append("  - (empty)\n");
        } else {
            map.forEach((key, value) ->
                    sb.append(String.format("  - %-15s : %s\n", key, value.getClass().getName()))
            );
        }
        sb.append("\n");
    }
}
