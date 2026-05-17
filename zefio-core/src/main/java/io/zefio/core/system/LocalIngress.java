package io.zefio.core.system;

import io.zefio.core.common.base.MDCKey;
import io.zefio.core.BaseIngress;
import io.zefio.core.IngressHandler;
import io.zefio.core.FireAndForgetCallback;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.util.MDCUtils;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ResponseListener;
import org.slf4j.MDC;

public class LocalIngress extends BaseIngress {

    // Internal storage for the callback handler to pass events to the Flow pipeline.
    protected IngressHandler ingressHandler;

    public LocalIngress(PluginContext context) {
        super(context);
    }

    @Override
    public String getDescription() {
        return "Passive Ingress for Internal Flow Call";
    }

    @Override
    public void initialise() throws Exception {
        // Initialize parent class if necessary
        super.initialise();

        // No actual port binding or listener registration logic.
        // Considered ready since FlowService is registered in the Registry.
        log.info("{} LocalIngress initialized. Waiting for internal events via [FLOW_CALL].", this.logHeader);
    }

    @Override
    public void receive(IngressHandler ingressHandler) {
        // Important: Only captures the handler without spawning threads or listeners.
        this.ingressHandler = ingressHandler;
    }

    /**
     * Memory entry point directly invoked by external flows (LocalUpstream).
     */
    public void inject(Payload payload, ResponseListener callerCallback) {
        MDCUtils.restoreMdc(payload); // Restore MDC

        MDC.put(MDCKey.FLOW.getKey(), this.flowName);
        payload.setMdcContext(MDC.getCopyOfContextMap());

        // Reset the statistical log suppression flag to default (false) since we entered a new flow.
        // This ensures that STAT logs are properly recorded in the new flow even if suppressed by the caller.
        payload.setSuppressStatLog(false);

        // Wrap the provided caller callback with an official listener.
        if (!isTwoWay()) {
            // Upstream flow is OneWay
            payload.setCallback(new FireAndForgetCallback(getMetricsAggregator()) {
                @Override
                public void success(Payload evt) {
                    if (callerCallback != null) callerCallback.success(evt); // Execute the provided callback
                    super.success(evt); // The core objective: Records the detailed log and STAT log of the current flow here.
                }
                @Override
                public void error(Payload evt) {
                    if (callerCallback != null) callerCallback.error(evt);
                    super.error(evt);
                }
            });
        } else {
            // For Two-Way, explicitly connect the callback provided by LocalUpstream.
            payload.setCallback(callerCallback);
        }

        this.ingressHandler.onPayload(payload); // Enqueue the payload
    }

    @Override
    public void close() {
        super.close();
        // No resources to clean up
    }
}
