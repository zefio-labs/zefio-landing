package io.zefio.gateway.filter.modify.delegate;

import io.zefio.core.payload.Payload;
import io.zefio.core.payload.util.TransferUtils;
import io.zefio.gateway.filter.modify.dto.FixedBinaryModifierChild;
import io.zefio.gateway.filter.modify.dto.FixedBinaryModifierDelegateValues;
import io.zefio.gateway.filter.modify.ValueModifierDirectional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

/**
 * Handles byte-level modifications for fixed-length binary or text payloads.
 */
public class FixedBinaryModifierDelegate implements ValueModifierDelegate {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final FixedBinaryModifierDelegateValues config;

    public FixedBinaryModifierDelegate(FixedBinaryModifierDelegateValues config) {
        this.config = config;
    }

    @Override
    public void modify(Payload payload, List<?> children, Charset encoding, ValueModifierDirectional parent) throws Exception {
        byte[] original = payload.getBody();
        byte[] body = Arrays.copyOf(original, original.length);

        log.info("Starting FixedBinary modification. Direction: {}, Children count: {}", config.getDirection(), children.size());

        for (Object obj : children) {
            FixedBinaryModifierChild child = (FixedBinaryModifierChild) obj;

            int offset = child.getOffset();
            Integer length = child.getLength();
            log.debug("Processing child offset [{}], length [{}], valueOrProperty [{}]", offset, length, child.getValueOrProperty());

            switch (config.getDirection()) {
                case MODIFY_BODY:
                    String replaceStr = TransferUtils.formConvertor(child.getValueOrProperty(), payload);
                    byte[] replaceBytes = replaceStr.getBytes(encoding);

                    if (offset < 0 || offset + replaceBytes.length > body.length) {
                        throw new IllegalArgumentException("Offset out of range for MODIFY_BODY");
                    }

                    System.arraycopy(replaceBytes, 0, body, offset, replaceBytes.length);
                    log.info("MODIFY_BODY: Replaced bytes at offset [{}] with [{}]", offset, replaceStr);
                    break;

                case PROPERTY_TO_BODY:
                    byte[] insertVal = (byte[]) payload.getHeader(child.getValueOrProperty());
                    if (insertVal == null) {
                        throw new IllegalArgumentException("Target property value is null: " + child.getValueOrProperty());
                    }

                    if (offset + insertVal.length > body.length) {
                        byte[] newBody = new byte[offset + insertVal.length];
                        System.arraycopy(body, 0, newBody, 0, body.length);
                        body = newBody;
                    }

                    System.arraycopy(insertVal, 0, body, offset, insertVal.length);
                    log.info("PROPERTY_TO_BODY: Inserted property [{}] at offset [{}]", child.getValueOrProperty(), offset);
                    break;

                case BODY_TO_PROPERTY:
                    if (length == null) {
                        throw new IllegalArgumentException("Length is required for BODY_TO_PROPERTY");
                    }

                    if (offset < 0 || offset + length > body.length) {
                        throw new IllegalArgumentException("Payload body length is insufficient for extraction");
                    }

                    byte[] extracted = new byte[length];
                    System.arraycopy(body, offset, extracted, 0, length);
                    payload.setHeader(child.getValueOrProperty(), extracted);
                    log.info("BODY_TO_PROPERTY: Extracted bytes from offset [{}], length [{}] to property [{}]", offset, length, child.getValueOrProperty());

                    if (config.getRemoveExtracted()) {
                        byte[] newData = new byte[body.length - length];
                        System.arraycopy(body, 0, newData, 0, offset);
                        System.arraycopy(body, offset + length, newData, offset, body.length - offset - length);
                        body = newData;
                        log.info("BODY_TO_PROPERTY: Removed extracted segment from body");
                    }
                    break;
            }
        }

        payload.setBody(body);
        log.info("FixedBinary body update complete");
    }
}
