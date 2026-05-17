package io.zefio.gateway.filter.transform;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.BaseComputeInterceptor;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.spel.PayloadExpressionEvaluator;
import io.zefio.core.payload.util.TelegramFactory;
import io.zefio.gateway.filter.transform.dto.SpELModifierInterceptorValues;
import io.zefio.gateway.filter.transform.dto.SpelAssignment;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Integrated filter using SpEL to extract, modify, or insert data in logical Maps
 * and perform a Write-Back to physical bytes.
 */
public class SpELModifierInterceptor extends BaseComputeInterceptor {

    private final SpELModifierInterceptorValues values;
    // Dedicated parser for value assignment (L-Value parsing)
    private static final ExpressionParser parser = new SpelExpressionParser();

    public SpELModifierInterceptor(PluginContext context) {
        super(context);
        this.values = yamlMapper.convertValue(context.getContext(), SpELModifierInterceptorValues.class);
    }

    @Override
    public String getDescription() {
        return "Integrated filter for data extraction/modification/insertion via SpEL with physical Write-Back.";
    }

    @Override
    public Payload process(Payload payload) throws FlowException {
        if (values.getAssignments() == null || values.getAssignments().isEmpty()) return payload;

        boolean isBodyModified = false;
        boolean isPropModified = false;
        SpelAssignment current = null;

        String telegramName = payload.getTelegramName();
        PayloadBuilder builder = TelegramFactory.getBuilder(telegramName);

        if (builder == null) {
            log.warn("[{}] Cannot modify body. Telegram [{}] not found in factory.", this.getPluginName(), telegramName);
            return payload; // Bypass since body cannot be manipulated without a builder
        }

        try {
            // Shadow Copy 1: Extract body as a mutable Map
            Map<String, Object> bodyMap = builder.parseToMap(payload.getBody(), payload.getCurrentEncoding());

            // Shadow Copy 2: Copy Event Headers to a mutable HashMap (defense against ImmutableMap)
            Map<String, Object> headersMap = new HashMap<>(payload.getHeaders());

            // Proxy Root: Create a mutable proxy layer for SpEL manipulation
            Map<String, Object> payloadProxy = new HashMap<>();
            payloadProxy.put("headers", headersMap);

            Map<String, Object> rootProxy = new HashMap<>();
            rootProxy.put("body", bodyMap);
            rootProxy.put("payload", payloadProxy);

            StandardEvaluationContext context = new StandardEvaluationContext(rootProxy);
            context.addPropertyAccessor(new MapAccessor());
            context.addPropertyAccessor(new ReflectivePropertyAccessor());

            for (SpelAssignment assignment : values.getAssignments()) {
                current = assignment;

                // Phase 1 (Read): Evaluate R-Value based on original event
                Object evaluatedValue = PayloadExpressionEvaluator.evaluate(assignment.getExpression(), payload, Object.class);

                // Phase 2 (Write): Assign value to the mutable proxy (L-Value)
                Expression targetExpr = parser.parseExpression(assignment.getTarget());
                targetExpr.setValue(context, evaluatedValue);

                if (assignment.getTarget().startsWith("body")) isBodyModified = true;
                if (assignment.getTarget().startsWith("payload.headers")) isPropModified = true;
            }

            // Final Sync 1: Synchronize Properties (Mutable Map -> Original Event)
            if (isPropModified) {
                headersMap.forEach(payload::setHeader);
            }

            // Final Sync 2: Synchronize Body (Mutable Map -> Physical Bytes)
            if (isBodyModified) {
                // Reassemble with byte precision using builder.buildFromMap
                byte[] newBodyBytes = builder.buildFromMap(bodyMap, payload.getCurrentEncoding());
                payload.setBody(newBodyBytes);

                // Set flag to alert Upstream flows of the modification
                payload.setBodyModified(true);
            }

            return payload;

        } catch (Exception e) {
            log.error("[{}] Data Alchemy Exploded! Target: [{}], Cause: {}",
                    this.getPluginName(), (current != null ? current.getTarget() : "NULL"), e.getMessage(), e);
            throw new FlowException(e, FlowResultStatus.MESSAGE_FORMAT_ERROR);
        }
    }
}
