package io.zefio.gateway.filter.security;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.BaseComputeInterceptor;
import io.zefio.core.factory.PluginContext;
import io.zefio.gateway.filter.security.dto.SpELValidatorInterceptorValues;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.spel.PayloadExpressionEvaluator;

/**
 * Business Guardrail Interceptor.
 * Evaluates a SpEL condition and performs a Fail-Fast block if the result is false.
 */
public class SpELValidatorInterceptor extends BaseComputeInterceptor {

    private final SpELValidatorInterceptorValues values;

    public SpELValidatorInterceptor(PluginContext context) {
        super(context);
        this.values = yamlMapper.convertValue(context.getContext(), SpELValidatorInterceptorValues.class);
    }

    @Override
    public String getDescription() {
        return "Business Guardrail filter that blocks (Fail-Fast) if a SpEL condition is evaluated as false.";
    }

    @Override
    public Payload process(Payload payload) throws FlowException {
        try {
            // 1. Evaluate as Object to avoid class cast exceptions during engine execution.
            Object result = PayloadExpressionEvaluator.evaluate(values.getCondition(), payload, Object.class);

            // 2. Strict Validation: Success only if the result is explicitly Boolean and true.
            // If the result is a non-boolean type (e.g., an Integer or an Object),
            // it is treated as a validation failure.
            boolean isValid = (result instanceof Boolean) && (Boolean) result;

            if (!isValid) {
                log.warn("[{}] Guardrail Blocked! Condition [{}] failed. Result: {}. TrxID: {}",
                        this.getPluginName(), values.getCondition(), result, payload.getTrxID());

                // Trigger security/business rejection
                throw new FlowException(FlowResultStatus.valueOf(values.getErrorStatus()), values.getErrorMessage());
            }

            if (log.isDebugEnabled()) {
                log.debug("[{}] Validation passed.", this.getPluginName());
            }

            // 3. Return the original payload to proceed to the next interceptor or Upstream.
            return payload;

        } catch (FlowException e) {
            // Propagate the intentional validation rejection error
            throw e;
        } catch (Exception e) {
            // 4. Handle internal SpEL engine errors (e.g., syntax errors in configuration)
            log.error("[{}] SpEL Evaluation Engine Error for condition: [{}]. Cause: {}",
                    this.getPluginName(), values.getCondition(), e.getMessage());
            throw new FlowException(e, FlowResultStatus.SPEL_EVALUATION_ERROR);
        }
    }
}
