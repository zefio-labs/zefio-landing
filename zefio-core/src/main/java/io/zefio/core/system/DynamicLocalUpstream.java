package io.zefio.core.system;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.system.dto.DynamicLocalUpstreamValues;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.spel.PayloadExpressionEvaluator;

public class DynamicLocalUpstream extends LocalUpstream {

    private final DynamicLocalUpstreamValues dynamicValues;

    public DynamicLocalUpstream(PluginContext context) {
        super(context);
        this.dynamicValues = yamlMapper.convertValue(context.getContext(), DynamicLocalUpstreamValues.class);
    }

    @Override
    public String getDescription() {
        return "Dynamic internal flow invocation via SpEL evaluation (Expression: " + dynamicValues.getTargetFlowExpression() + ")";
    }

    @Override
    public Payload blockingProcessInternal(Payload payload) throws FlowException {
        String targetName = null;
        try {
            targetName = PayloadExpressionEvaluator.evaluate(dynamicValues.getTargetFlowExpression(), payload, String.class);
        } catch (Exception e) {
            throw new FlowException(e, FlowResultStatus.SPEL_EVALUATION_ERROR);
        }
        if (targetName == null || targetName.isEmpty()) {
            throw new FlowException(FlowResultStatus.INVALID_INPUT, "Evaluated Dynamic Target Flow is null or empty");
        }
        targetName = targetName.trim();
        log.debug("Preparing to call dynamic target flow [{}] (Mode: {})", targetName, isTwoWay() ? "TwoWay" : "OneWay");

        // Delegate the actual invocation logic to the parent engine using the resolved name.
        return callInternalFlow(targetName, payload, dynamicValues.getTimeoutOrDefault());
    }
}
