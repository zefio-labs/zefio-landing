package io.zefio.core.system;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.BaseComputeInterceptor;
import io.zefio.core.system.dto.AsyncResumeInterceptorValues;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import org.apache.commons.lang3.StringUtils;

/**
 * Interceptor responsible for resuming a suspended asynchronous flow.
 * It extracts a correlation key from the incoming response and notifies the FlowSyncBridge.
 */
public class AsyncResumeInterceptor extends BaseComputeInterceptor {

    private final AsyncResumeInterceptorValues values;

    public AsyncResumeInterceptor(PluginContext context) {
        super(context);
        this.values = yamlMapper.convertValue(context.getContext(), AsyncResumeInterceptorValues.class);
    }

    @Override
    public String getDescription() {
        String keySource = StringUtils.isNotBlank(values.getBridgeKeyProperty()) ?
                "Property[" + values.getBridgeKeyProperty() + "]" : "Default Transaction ID (TrxID)";

        return "Extracts the " + keySource + " from the arrived response and notifies the waiting flow. " +
                "(If bridgeKeyProperty is not configured, it defaults to using the Transaction ID (TrxID) for matching.)";
    }

    @Override
    public Payload process(Payload payload) throws FlowException {
        // 1. Extract correlation key
        String key = payload.getTrxID();

        if (StringUtils.isNotBlank(values.getBridgeKeyProperty())) {
            Object customKeyObj = payload.getHeader(values.getBridgeKeyProperty());
            if (customKeyObj != null && StringUtils.isNotBlank(customKeyObj.toString())) {
                key = customKeyObj.toString().trim();
            } else {
                log.warn("Notification skipped: Specified bridge key property [{}] not found in payload headers.", values.getBridgeKeyProperty());
                return payload;
            }
        }

        if (StringUtils.isBlank(key)) {
            log.warn("Notification failed: Correlation key is blank.");
            return payload;
        }

        // 2. Notify the SyncBridge of completion (pass a copy of the payload to prevent reference mutation)
        Payload responseSnapshot = payload.copyFactory(payload);
        boolean notified = this.syncBridge.complete(key, responseSnapshot);

        if (notified) {
            log.info("Response matched successfully - Key: [{}]", key);
        } else {
            log.debug("Unmatched response: No waiting thread found for Key [{}]. It may have timed out or already completed.", key);
        }

        return payload;
    }
}
