package io.zefio.gateway.filter.modify;

import com.google.common.collect.Lists;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.builder.config.Telegram;
import io.zefio.core.payload.util.TelegramFactory;
import io.zefio.gateway.filter.modify.dto.FixedBinaryModifierDelegateValues;
import io.zefio.gateway.filter.modify.dto.JsonValueModifierDelegateValues;
import io.zefio.gateway.filter.modify.dto.XmlValueModifierDelegateValues;
import io.zefio.gateway.filter.modify.delegate.FixedBinaryModifierDelegate;
import io.zefio.gateway.filter.modify.delegate.JsonValueModifierDelegate;
import io.zefio.gateway.filter.modify.delegate.ValueModifierDelegate;
import io.zefio.gateway.filter.modify.delegate.XmlValueModifierDelegate;

import java.nio.charset.Charset;
import java.util.List;

/**
 * Directional implementation of the Value Modifier.
 * Dynamically selects a delegate (JSON, XML, or Fixed) based on the Ingress telegram type
 * defined in the context.
 */
public class ValueModifierDirectional extends AbstractValueModifier {

    private final ValueModifierDelegate delegate;
    private final Object config;

    public ValueModifierDirectional(PluginContext context) {
        super(context);

        // Identify the telegram name from the Ingress context
        String ingressTelegramName = context.getTelegramName();
        if (ingressTelegramName == null) {
            throw new IllegalArgumentException("Modification filter requires a valid Ingress telegram name from the context.");
        }

        // Determine the telegram type to initialize the correct delegate and configuration
        Telegram.Type type = TelegramFactory.getBuilder(ingressTelegramName).getTelegram().getType();
        switch (type) {

            case JSON:
                this.config = yamlMapper.convertValue(context.getContext(), JsonValueModifierDelegateValues.class);
                this.delegate = new JsonValueModifierDelegate((JsonValueModifierDelegateValues) this.config);
                break;

            case XML:
                this.config = yamlMapper.convertValue(context.getContext(), XmlValueModifierDelegateValues.class);
                this.delegate = new XmlValueModifierDelegate((XmlValueModifierDelegateValues) this.config);
                break;

            case Fixed:
                this.config = yamlMapper.convertValue(context.getContext(), FixedBinaryModifierDelegateValues.class);
                this.delegate = new FixedBinaryModifierDelegate((FixedBinaryModifierDelegateValues) this.config);
                break;

            default:
                throw new IllegalArgumentException("Unsupported telegram type for modification: " + type);
        }
    }

    @Override
    protected List<?> getChildren() {
        // Return the specific children list based on the loaded configuration type
        if (config instanceof JsonValueModifierDelegateValues) {
            return ((JsonValueModifierDelegateValues) config).getChildren();
        } else if (config instanceof XmlValueModifierDelegateValues) {
            return ((XmlValueModifierDelegateValues) config).getChildren();
        } else if (config instanceof FixedBinaryModifierDelegateValues) {
            return ((FixedBinaryModifierDelegateValues) config).getChildren();
        }
        return Lists.newArrayList();
    }

    @Override
    protected void doModify(Payload payload, List<?> children, Charset encoding) throws Exception {
        // Delegate the modification task to the specialized format-specific delegate
        delegate.modify(payload, children, encoding, this);
    }
}
