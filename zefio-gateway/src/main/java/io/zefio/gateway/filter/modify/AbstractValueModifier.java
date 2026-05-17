package io.zefio.gateway.filter.modify;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.BaseComputeInterceptor;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;

import java.nio.charset.Charset;
import java.util.List;

/**
 * Abstract base class for directional value modifiers.
 * Provides the core execution logic for modifying specific offsets, JSONPaths,
 * or XPaths within the message body.
 */
public abstract class AbstractValueModifier extends BaseComputeInterceptor {

    public AbstractValueModifier(PluginContext context) {
        super(context);
    }

    @Override
    public String getDescription() {
        return "Directional Value Modifier that inserts, modifies, or extracts values " +
                "using specific Offsets, JSONPaths, or XPaths within the body.";
    }

    @Override
    public Payload process(Payload payload) throws FlowException {
        try {
            Charset encoding = payload.getCurrentEncoding();
            List<?> children = getChildren();

            if (children == null || children.isEmpty()) {
                return payload;
            }

            doModify(payload, children, encoding);
            return payload;
        } catch (Exception e) {
            // Handle unexpected exceptions occurring during the modification process
            log.error("Error occurred during data modification: {}", e.getMessage());
            throw new FlowException(e, FlowResultStatus.INVALID_INPUT);
        }
    }

    /**
     * Retrieves the list of modification rules (children) defined in the configuration.
     */
    protected abstract List<?> getChildren();

    /**
     * Executes the specific modification logic based on the message format.
     */
    protected abstract void doModify(Payload payload, List<?> children, Charset encoding) throws Exception;
}
