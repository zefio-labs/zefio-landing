package io.zefio.core;

import io.zefio.core.factory.PluginContext;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Abstract class for scheduled polling Ingress modules.
 * Periodically executes polling logic and dispatches results to the reactive pipeline.
 */
public abstract class ScheduledIngress extends ReactiveIngress {

    protected long pollingIntervalMillis = 30000L;

    public ScheduledIngress(PluginContext context) {
        super(context);
    }

    @Override
    protected void doStart() throws Exception {
        // Register periodic polling task to the global shared scheduled pool
        context.getSharedScheduledPool().scheduleWithFixedDelay(() -> {
            try {
                // Implementation classes (e.g., FtpIngress) fetch raw data
                List<Object> rawDatas = doPoll();

                for (Object rawData : rawDatas) {
                    // Call the asynchronous entry point of the parent ReactiveIngress
                    onAsyncIngressMessage(rawData, "Polling-" + this.pluginName);
                }
            } catch (Exception e) {
                log.error("{} Polling execution failed", this.logHeader, e);
            }
        }, 0, pollingIntervalMillis, TimeUnit.MILLISECONDS);

        log.info("{} Polling Ingress Started (Interval: {}ms)", this.logHeader, pollingIntervalMillis);
    }

    /**
     * Periodic execution logic to be implemented by child classes.
     * Returns a list of raw objects (e.g., byte[] or String).
     */
    protected abstract List<Object> doPoll() throws Exception;
}
