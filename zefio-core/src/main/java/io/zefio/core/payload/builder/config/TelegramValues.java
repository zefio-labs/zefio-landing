package io.zefio.core.payload.builder.config;

/**
 * Interface defining the common configuration properties required for all telegram formats.
 * This includes encoding behavior, correlation ID extraction, and packet framing settings.
 */
public interface TelegramValues {
    boolean getEncodingIgnore();
    void setEncodingIgnore(boolean ignore);

    CorrelationField getCorrelation();

    /**
     * Provides unified framing configuration for identifying packet boundaries.
     */
    FramingField getFraming();
}
