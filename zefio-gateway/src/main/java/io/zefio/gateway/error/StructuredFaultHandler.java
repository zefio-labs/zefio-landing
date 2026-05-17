package io.zefio.gateway.error;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.BaseComputeFaultHandler;
import io.zefio.core.factory.PluginContext;
import io.zefio.gateway.error.common.ErrorMessageEditor;
import io.zefio.gateway.error.common.FixedErrorEditor;
import io.zefio.gateway.error.common.JsonErrorEditor;
import io.zefio.gateway.error.common.XmlErrorEditor;
import io.zefio.gateway.error.dto.FixedFaultValues;
import io.zefio.gateway.error.dto.JsonFaultValues;
import io.zefio.gateway.error.dto.XmlFaultValues;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.Telegram;
import io.zefio.core.payload.util.TelegramFactory;

import java.nio.charset.Charset;

/**
 * StructuredFaultHandler
 * Generates structured error messages based on the identified Telegram type (JSON/XML/FIXED).
 */
public class StructuredFaultHandler extends BaseComputeFaultHandler {

    // Volatile keyword used for thread-safe lazy-loading in multi-threaded environments.
    private volatile ErrorMessageEditor editor;
    private String initializedTelegramName;

    public StructuredFaultHandler(PluginContext context) {
        super(context);

        // [Initialization Strategy] Proactive warm-up if the telegram name exists at startup.
        String initialName = context.getTelegramName();
        if (initialName != null) {
            try {
                log.debug("[{}] Proactively initializing editor with: {}", pluginName, initialName);
                ensureEditorInitialized(initialName);
            } catch (Exception e) {
                // Log warning instead of throwing an exception during startup to allow for runtime retry.
                log.warn("[{}] Proactive initialization failed (will retry at runtime): {}", pluginName, e.getMessage());
            }
        }
    }

    @Override
    public Payload process(Payload payload) throws FlowException {
        // 1. Identify current telegram specification.
        String currentName = (payload.getTelegramName() != null) ? payload.getTelegramName() : context.getTelegramName();

        // Hybrid check: Initialize if null or handle Dynamic Switching if the specification changes during runtime.
        if (editor == null || !currentName.equals(initializedTelegramName)) {
            if (currentName == null) {
                throw new FlowException(FlowResultStatus.MESSAGE_FORMAT_ERROR, "Telegram name is missing in both Event and Context.");
            }
            ensureEditorInitialized(currentName);
        }

        Charset encoding = payload.getCurrentEncoding();
        Throwable throwable = payload.getThrowable();
        try {
            byte[] result = editor.edit(payload, encoding, throwable);
            payload.setBody(result);
        } catch (Exception e) {
            log.error("[{}] Critical error during editing: {}", pluginName, e.getMessage());

            handleFallback(payload, encoding, e);

            // Wrap and throw as FlowException for subsequent processing (Body is already filled with fallback data).
            throw new FlowException(e, FlowResultStatus.MESSAGE_FORMAT_ERROR);
        }

        return payload;
    }

    @Override
    public String getDescription() {
        return "Error Filter that generates structured error messages according to the Telegram type (JSON/XML/FIXED).";
    }

    /**
     * [Double-Checked Locking] Thread-safe intelligent initialization for optimized performance.
     */
    private void ensureEditorInitialized(String telegramName) {
        synchronized (this) {
            // Skip if already initialized with the same name by another thread.
            if (editor != null && telegramName.equals(initializedTelegramName)) {
                return;
            }

            try {
                PayloadBuilder builder = TelegramFactory.getBuilder(telegramName);
                if (builder == null) {
                    throw new IllegalArgumentException("No telegram layout found for: " + telegramName);
                }

                Telegram.Type type = builder.getTelegram().getType();
                log.info("[{}] Initializing editor for type: {} ({})", pluginName, type, telegramName);

                switch (type) {
                    case JSON:
                        this.editor = new JsonErrorEditor(yamlMapper.convertValue(context.getContext(), JsonFaultValues.class));
                        break;
                    case XML:
                        this.editor = new XmlErrorEditor(yamlMapper.convertValue(context.getContext(), XmlFaultValues.class));
                        break;
                    case Fixed:
                        this.editor = new FixedErrorEditor(yamlMapper.convertValue(context.getContext(), FixedFaultValues.class));
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported telegram type: " + type);
                }
                this.initializedTelegramName = telegramName;

            } catch (FlowException fe) {
                throw fe;
            } catch (Exception e) {
                // Propagate as internal server error upon initialization failure.
                throw new FlowException(e, FlowResultStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    /**
     * [Final Defense] Ensures minimal data integrity if error message generation fails.
     */
    private void handleFallback(Payload payload, Charset encoding, Exception e) {
        String fallbackMsg = String.format("Error Format Failure: %s", e.getMessage());
        byte[] bytes = fallbackMsg.getBytes(encoding);

        // Ensure header length alignment for fixed-length environments.
        payload.setBody(safeAppendFixedLength(payload, bytes));
    }
}
