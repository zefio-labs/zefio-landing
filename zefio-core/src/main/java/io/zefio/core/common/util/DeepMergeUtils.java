package io.zefio.core.common.util;

import java.util.HashMap;
import java.util.Map;

/**
 * A utility class designed for deep-merging Map structures.
 * Unlike standard map operations that overwrite keys entirely,
 * this utility recursively merges nested maps, ensuring that
 * configurations or complex data structures are combined correctly.
 */
public class DeepMergeUtils {

    public static Map<String, Object> mergeClone(Map<String, Object> base, Map<String, Object> override) {
        Map<String, Object> result = new HashMap<>();
        if (base != null) merge(result, base);
        if (override != null) merge(result, override);
        return result;
    }

    @SuppressWarnings("unchecked")
    public static void merge(Map<String, Object> target, Map<String, Object> source) {
        if (source == null) return;

        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object sourceValue = entry.getValue();

            if (sourceValue instanceof Map) {
                if (!target.containsKey(key) || !(target.get(key) instanceof Map)) {
                    target.put(key, new HashMap<>());
                }
                merge((Map<String, Object>) target.get(key), (Map<String, Object>) sourceValue);
            } else {
                target.put(key, sourceValue);
            }
        }
    }
}
