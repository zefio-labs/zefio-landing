package io.zefio.gateway.quartz.job;

import io.zefio.gateway.quartz.base.QuartzIngressObject;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Pure asynchronous boundary event producer driven by the Quartz engine.
 * Forwards raw data maps directly to the SEDA ingress pipelines to prevent
 * serialization loops and thread starvation.
 */
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class ZefioFlowTriggerJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(ZefioFlowTriggerJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        QuartzIngressObject dvo = (QuartzIngressObject) jobDataMap.get("base");

        if (dvo == null || dvo.getIngress() == null) {
            throw new JobExecutionException("Fatal: Missing system QuartzIngressObject context data within JobDataMap structure.");
        }

        // Increment the metric registry counters tracking total inbound payloads received
        dvo.getIngress().getMetricsAggregator().incrementPayloadReceivedCount();

        // 1. Construct the raw data map acting as the stream trigger context
        Map<String, Object> analyticalBodyContext = new HashMap<>();
        if (dvo.getValueContext() != null) {
            analyticalBodyContext.putAll(dvo.getValueContext());
        }

        // Ensure baseline geographic attributes are present to support downstream interceptors
        analyticalBodyContext.putIfAbsent("target_location", "Icheon");
        analyticalBodyContext.putIfAbsent("commodity_code", "200");

        if (log.isDebugEnabled()) {
            log.debug("[ZefioFlowTriggerJob] Emitting raw trigger context map: {}", analyticalBodyContext);
        }

        // 2. Pass the RAW data structure directly to the engine boundary.
        // The framework will capture the map, convert it to bytes via the configured 'json-standard'
        // layout builder, and automatically generate a new transaction UUID if none is provided.
        dvo.getIngress().onAsyncIngressMessage(analyticalBodyContext, "QuartzScheduledJobTrigger");
    }
}
