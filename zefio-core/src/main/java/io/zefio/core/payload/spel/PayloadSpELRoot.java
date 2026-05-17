package io.zefio.core.payload.spel;

import io.zefio.core.payload.Payload;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.Telegram;
import io.zefio.core.payload.util.TelegramFactory;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * The root object provided to the SpEL evaluation context.
 * It provides format-independent access to the payload body through lazy parsing.
 */
public class PayloadSpELRoot {
    private static final Logger log = LoggerFactory.getLogger(PayloadSpELRoot.class);

    @Getter
    private final Payload payload;
    private Map<String, Object> parsedBody = null;

    // Cache the parsing attempt status to prevent redundant parsing failures
    private boolean isParsed = false;

    public PayloadSpELRoot(Payload payload) {
        this.payload = payload;
    }

    /**
     * Provides format-independent body access.
     * Expression example: "#{body.header.trxId}" (works for JSON, XML, and Fixed formats).
     */
    public Map<String, Object> getBody() {
        // Return immediately if parsing has already been attempted (cached result)
        if (this.isParsed) {
            return this.parsedBody;
        }

        byte[] bodyBytes = payload.getBody();
        if (bodyBytes == null || bodyBytes.length == 0) {
            log.debug("[SpEL-Root] Body is empty. Skipping parse.");
            this.isParsed = true;
            return null;
        }

        String telegramName = payload.getTelegramName();
        if (telegramName == null) {
            log.warn("[SpEL-Root] Telegram name is missing in Payload. Cannot parse body.");
            this.isParsed = true;
            return null;
        }

        // Retrieve the appropriate PayloadBuilder (JSON, XML, or Fixed) from the factory
        PayloadBuilder builder = TelegramFactory.getBuilder(telegramName);
        if (builder == null) {
            log.warn("[SpEL-Root] No PayloadBuilder found for telegram: [{}].", telegramName);
            this.isParsed = true;
            return null;
        }

        try {
            long start = System.currentTimeMillis();
            // Convert raw bytes to a logical Map for expression traversal
            this.parsedBody = builder.parseToMap(bodyBytes, payload.getCurrentEncoding());
            long elapsed = System.currentTimeMillis() - start;

            if (log.isDebugEnabled()) {
                log.debug("[SpEL-Root] Lazy Parsing completed. Fields: [{}], Elapsed: {}ms",
                        (parsedBody != null ? parsedBody.size() : 0), elapsed);
            }
        } catch (Exception e) {
            log.error("[SpEL-Root] Parsing failed during SpEL evaluation.", e);
            this.parsedBody = null;
        } finally {
            // Mark as parsed regardless of outcome to prevent infinite retry loops
            this.isParsed = true;
        }

        return this.parsedBody;
    }

    /**
     * Exposes telegram metadata rather than the underlying builder engine.
     * Expression examples: "#{telegram.name}", "#{telegram.type}"
     */
    public Telegram getTelegram() {
        String telegramName = payload.getTelegramName();
        if (telegramName == null) return null;

        PayloadBuilder builder = TelegramFactory.getBuilder(telegramName);
        return (builder != null) ? builder.getTelegram() : null;
    }
}
