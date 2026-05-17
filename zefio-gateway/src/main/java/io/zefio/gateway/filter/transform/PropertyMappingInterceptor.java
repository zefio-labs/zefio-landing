package io.zefio.gateway.filter.transform;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.BaseComputeInterceptor;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.gateway.filter.transform.dto.PropertyMappingInterceptorValues;

import java.util.Map;

/**
 * Interceptor for dynamic value mapping and override control between Event Properties.
 */
public class PropertyMappingInterceptor extends BaseComputeInterceptor {
    private final PropertyMappingInterceptorValues values;

    public PropertyMappingInterceptor(PluginContext context) {
        super(context);
        this.values = yamlMapper.convertValue(context.getContext(), PropertyMappingInterceptorValues.class);
    }

    @Override
    public String getDescription() {
        return "Controls dynamic value mapping and overrides between Event Properties.";
    }

    @Override
    public Payload process(Payload payload) throws FlowException {
        if (!values.hasMappings()) return payload;

        for (Map.Entry<String, String> entry : values.getMappings().entrySet()) {
            String targetKey = entry.getKey();
            String sourceKey = entry.getValue();

            // 1. Overwrite Check: Handle the logic for preventing unintended overwrites
            if (!values.isOverwrite() && payload.containsKeyHeader(targetKey)) {
                log.debug("Skip mapping: targetKey [{}] already exists (overwrite: false)", targetKey);
                continue;
            }

            Object sourceValue = payload.getHeader(sourceKey);

            // 2. Null Source Check: Handle missing source values
            if (sourceValue == null && values.isIgnoreNullSource()) {
                log.debug("Skip mapping: sourceKey [{}] is null (ignoreNullSource: true)", sourceKey);
                continue;
            }

            // 3. Execution: Perform the actual mapping
            payload.setHeader(targetKey, sourceValue);
            log.debug("Property Mapped: [{}] -> [{}] = {}", sourceKey, targetKey, sourceValue);
        }

        return payload;
    }
}
