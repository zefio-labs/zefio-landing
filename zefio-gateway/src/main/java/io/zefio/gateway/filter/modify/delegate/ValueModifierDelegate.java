package io.zefio.gateway.filter.modify.delegate;

import io.zefio.core.payload.Payload;
import io.zefio.gateway.filter.modify.ValueModifierDirectional;

import java.nio.charset.Charset;
import java.util.List;

/**
 * Interface for delegating specific payload modification logic based on the message format.
 */
public interface ValueModifierDelegate {
    /**
     * Executes the modification logic for the given payload.
     *
     * @param payload  The payload to modify.
     * @param children List of modifier configurations.
     * @param encoding The charset encoding to use.
     * @param parent   The parent modifier filter instance.
     * @throws Exception If modification fails.
     */
    void modify(Payload payload, List<?> children, Charset encoding, ValueModifierDirectional parent) throws Exception;
}
