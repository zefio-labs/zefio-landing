package io.zefio.gateway.http;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.BaseComputeInterceptor;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.PayloadHeaders;
import io.zefio.core.payload.spel.PayloadExpressionEvaluator;
import io.zefio.gateway.http.dto.HttpHeaderValeSetterValues;

import java.util.Map;

/**
 * Interceptor that dynamically generates and maps HTTP request/response headers
 * by evaluating Spring Expression Language (SpEL) against the current Payload context.
 */
public class HttpHeaderValeSetter extends BaseComputeInterceptor {

    private final HttpHeaderValeSetterValues values;

    public HttpHeaderValeSetter(PluginContext context) {
        super(context);
        this.values = yamlMapper.convertValue(context.getContext(), HttpHeaderValeSetterValues.class);
    }

    @Override
    public String getDescription() {
        return "Dynamically resolves and injects HTTP headers based on SpEL configurations.";
    }

    @Override
    public Payload process(Payload payload) throws FlowException {
        // Determine the target namespace prefix based on the interceptor's configuration (Request vs Response)
        String prefix = (values.getTargetType() == HttpHeaderValeSetterValues.HttpHeaderType.REQUEST)
                ? PayloadHeaders.HTTP_REQUEST_PREFIX  // "http.req."
                : PayloadHeaders.HTTP_RESPONSE_PREFIX; // "http.res."

        for (Map.Entry<String, String> entry : values.getHeaderKeyValues().entrySet()) {
            String key = entry.getKey();
            String expression = entry.getValue();

            try {
                // Core Logic: Evaluate the SpEL expression to extract the actual value dynamically.
                // Using Object.class ensures it can handle diverse return types gracefully.
                Object resolvedValue = PayloadExpressionEvaluator.evaluate(expression, payload, Object.class);
                String finalValue = (resolvedValue == null) ? "" : String.valueOf(resolvedValue);

                // Inject the resolved value into the payload headers with the appropriate prefix
                payload.setHeader(prefix + key, finalValue);

                if (log.isInfoEnabled()) {
                    log.info("[{}] Header Map: {} -> {}", this.getPluginName(), key, finalValue);
                }

            } catch (Exception e) {
                log.error("[{}] Header Resolution Failed. Key: {}, Expr: {}", this.getPluginName(), key, expression);
                throw new FlowException(e, FlowResultStatus.SPEL_EVALUATION_ERROR);
            }
        }
        return payload;
    }
}
