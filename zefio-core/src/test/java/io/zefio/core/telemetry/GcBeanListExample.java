package io.zefio.core.telemetry;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class to inspect and list active JVM Garbage Collector MXBeans.
 * Used to identify supported GC metrics for the JvmMonitorLogger.
 */
public class GcBeanListExample {

    public static void main(String[] args) {
        // 1. Retrieve the list of all GarbageCollectorMXBeans via ManagementFactory
        List<GarbageCollectorMXBean> gcMXBeans = ManagementFactory.getGarbageCollectorMXBeans();

        System.out.println("--- JVM Active Garbage Collector MXBeans ---");

        // 2. Iterate through the list and print detailed information for each Bean
        for (GarbageCollectorMXBean bean : gcMXBeans) {
            System.out.println("----------------------------------------");
            System.out.println("Name: " + bean.getName());
            System.out.println("Collection Count: " + bean.getCollectionCount());
            System.out.println("Collection Time (ms): " + bean.getCollectionTime());
            System.out.println("Memory Pool Names: " + String.join(", ", bean.getMemoryPoolNames()));
        }
        System.out.println("----------------------------------------");

        // 3. Output GC names as a single comma-separated string for JvmMonitorLogger configuration
        String allGcNames = gcMXBeans.stream()
                .map(GarbageCollectorMXBean::getName)
                .collect(Collectors.joining(", "));

        System.out.println("\nAll GC Names for JvmMonitorLogger:");
        System.out.println(allGcNames);
    }
}
