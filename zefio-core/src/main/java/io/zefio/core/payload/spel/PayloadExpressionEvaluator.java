package io.zefio.core.payload.spel;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.zefio.core.payload.Payload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.concurrent.TimeUnit;

/**
 * Orchestrates SpEL evaluation logic for the engine.
 * It uses a high-performance cache for parsed expressions and a shared MapAccessor
 * to allow SpEL to navigate the payload body using dot-notation (e.g., body.user.id).
 */
public class PayloadExpressionEvaluator {
    private static final Logger log = LoggerFactory.getLogger(PayloadExpressionEvaluator.class);
    private static final ExpressionParser parser = new SpelExpressionParser();

    // Cache parsed Expression objects to prevent OOM and ensure O(1) performance
    private static final Cache<String, Expression> expressionCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    // Reusable MapAccessor to avoid redundant object creation during evaluation
    private static final MapAccessor MAP_ACCESSOR = new MapAccessor();

    public static String evaluateString(String expressionString, Payload payload) {
        return evaluate(expressionString, payload, null, String.class);
    }

    public static <T> T evaluate(String expressionString, Payload payload, Class<T> expectedType) {
        return evaluate(expressionString, payload, null, expectedType);
    }

    /**
     * Evaluates a SpEL expression string with optional Throwable context.
     *
     * @param expressionString The template expression (e.g., "#{body.amount > 1000}")
     * @param payload The current transaction payload
     * @param throwable Optional exception object for error-handling expressions (accessible via #exception)
     * @param expectedType The expected return type
     */
    public static <T> T evaluate(String expressionString, Payload payload, Throwable throwable, Class<T> expectedType) {
        // Handle literal strings for backward compatibility
        if (expressionString == null || !expressionString.contains("#{")) {
            return expectedType.isInstance(expressionString) ? expectedType.cast(expressionString) : null;
        }

        if (log.isDebugEnabled()) {
            log.debug("[SpEL] Evaluating: [{}] (TrxID: {}, HasException: {})",
                    expressionString, payload.getTrxID(), (throwable != null));
        }

        // Retrieve compiled Abstract Syntax Tree (AST) from cache
        Expression expression = expressionCache.get(expressionString, key ->
                parser.parseExpression(key, ParserContext.TEMPLATE_EXPRESSION)
        );

        EvaluationContext context = createEvaluationContext(payload, throwable);

        try {
            T result = expression.getValue(context, expectedType);
            if (log.isDebugEnabled()) {
                log.debug("[SpEL] Result: [{}] -> [{}]", expressionString, result);
            }
            return result;
        } catch (Exception e) {
            log.error("[SpEL] Evaluation Failed: {} | Error: {}", expressionString, e.getMessage());
            throw new IllegalArgumentException("SpEL evaluation error: " + expressionString, e);
        }
    }

    /**
     * Configures the StandardEvaluationContext.
     * 1. MapAccessor: Allows SpEL to treat Map keys as object properties.
     * 2. Variable Injection: Registers the #exception keyword for Throwable access.
     */
    private static EvaluationContext createEvaluationContext(Payload payload, Throwable throwable) {
        PayloadSpELRoot root = new PayloadSpELRoot(payload);
        StandardEvaluationContext context = new StandardEvaluationContext(root);

        // Enables dot-notation for nested Maps (e.g., body.HEADER.MSG_TYPE)
        context.addPropertyAccessor(MAP_ACCESSOR);

        // Registers #exception variable for use in YAML expressions
        if (throwable != null) {
            context.setVariable("exception", throwable);
        }

        return context;
    }
}
