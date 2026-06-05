package io.zefio.core;

import io.zefio.core.common.base.PluginType;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.schema.dto.TwowayIngressValues;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.ExchangePattern;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.util.TelegramFactory;
import io.zefio.core.telemetry.module.ModuleMetricsAggregator;

import java.nio.charset.Charset;

/**
 * Base implementation for all Ingress modules.
 * Manages common properties such as encoding, exchange patterns, and Telegram builders.
 * Implements the universal hot-swap interface step to support polymorphic unbinding.
 */
public abstract class BaseIngress extends BaseGatewayPlugin implements Ingress {
    protected ExchangePattern exchangePattern;
    protected final String ingressTelegramName;
    protected volatile PayloadBuilder ingressBuilder;

    protected long transactionTimeoutMillis;

    protected final String logHeader;
    protected final Charset requestEncoding;
    protected final Charset responseEncoding;

    public BaseIngress(PluginContext context) {
        super(context, new ModuleMetricsAggregator(PluginType.ingress, context.getFlowName() + "-" + context.getPluginName()));

        this.exchangePattern = context.getExchangePattern();
        this.ingressTelegramName = context.getTelegramName();

        TwowayIngressValues values = yamlMapper.convertValue(context.getContext(), TwowayIngressValues.class);
        this.requestEncoding = values.getRequestEncoding();
        this.responseEncoding = values.getResponseEncoding();
        this.logHeader = String.format("[%s] ", this.pluginName);
    }

    @Override
    public void initialise() throws Exception {
        // Pre-warm the Telegram Builder cache during initialization
        getEventBuilder();
        super.initialise();
    }

    @Override
    public PayloadBuilder getEventBuilder() {
        // Fast Path: Return from cache if already initialized
        if (ingressBuilder != null) {
            return ingressBuilder;
        }

        // Slow Path: Synchronized initialization from factory
        synchronized (this) {
            if (ingressBuilder == null) {
                PayloadBuilder builder = TelegramFactory.getBuilder(ingressTelegramName);
                if (builder == null) {
                    throw new FlowException(FlowResultStatus.MESSAGE_FORMAT_ERROR, "Telegram Builder not found: " + ingressTelegramName);
                }
                this.ingressBuilder = builder;
            }
        }
        return this.ingressBuilder;
    }

    /**
     * Clears the cached Telegram Builder to support hot-reloading of configurations.
     */
    @Override
    public void refresh() {
        this.ingressBuilder = null;
    }

    @Override
    public boolean isTwoWay() {
        return this.exchangePattern == ExchangePattern.RequestReply;
    }

    /**
     * 🎯 [Polymorphic Hot-Swap Step]
     * Default fallback implementation for the Ingress unbinding interface.
     * Passive ingresses (like LocalIngress) inherit this as a safe No-Op action.
     * Network-facing ingresses (like BaseNettyIngress) must override this to release kernel descriptors.
     */
    @Override
    public void stopListening() {
        log.debug("{} Ingress type does not allocate network descriptors. Skipping physical unbind.", this.logHeader);
    }

    /**
     * Helper method to log fatal startup errors and trigger system-level alerts.
     */
    protected void logFatalStartupError(Exception e) {
        FlowException flowEx = new FlowException(e, FlowResultStatus.SYSTEM_SHUTDOWN);
        log.error("{} Ingress Startup Failed - {}", this.logHeader, flowEx.getMessage(), flowEx);
    }
}
