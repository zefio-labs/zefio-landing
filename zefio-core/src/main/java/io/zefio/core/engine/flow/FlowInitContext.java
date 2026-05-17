package io.zefio.core.engine.flow;

import io.micrometer.core.instrument.MeterRegistry;
import io.zefio.core.config.flow.FlowOptions;
import io.zefio.core.config.monitor.MonitorProperties;
import io.zefio.core.Ingress;
import io.zefio.core.engine.policy.ExceptionPolicyManager;
import io.zefio.core.engine.pool.SharedPools;
import io.zefio.core.engine.processor.Processor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * A context object containing the configuration and resources required to initialize a message flow.
 * It holds references to the pipeline root, shared resource pools, and error handling policies.
 */
@Getter
@Builder
public class FlowInitContext {
    private final String flowName;
    private final String flowLabel;
    private final FlowOptions options;
    private final Ingress ingress;

    // Pipeline root object list used for recursive processing
    private final List<Processor> rootPipeline;

    // Global and flow-level error control pipelines
    private final Map<String, List<Processor>> errorPipelines;

    private final ExceptionPolicyManager policyManager;
    private final SharedPools sharedPools;
    private final MeterRegistry meterRegistry;
    private final MonitorProperties monitorProperties;
}
