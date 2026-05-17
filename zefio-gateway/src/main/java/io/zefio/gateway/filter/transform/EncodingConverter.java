package io.zefio.gateway.filter.transform;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.BaseComputeInterceptor;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.util.BytesUtils;
import io.zefio.gateway.filter.transform.dto.EncodingConverterValues;
import org.apache.commons.lang3.ObjectUtils;

import java.nio.charset.Charset;

/**
 * Filter that changes the data encoding format of the payload body.
 */
public class EncodingConverter extends BaseComputeInterceptor {
    private final EncodingConverterValues values;

    public EncodingConverter(PluginContext context) {
        super(context);
        this.values = yamlMapper.convertValue(context.getContext(), EncodingConverterValues.class);
    }

    @Override
    public String getDescription() { return "Filter to change the data encoding format of the payload."; }

    @Override
    public Payload process(Payload payload) throws FlowException {
        if (ObjectUtils.isNotEmpty(this.values.getChangeEncoding())) {
            Charset originalEncoding = payload.getCurrentEncoding();
            byte[] convertedBytes = BytesUtils.changeEncoding(payload.getBody(), originalEncoding, this.values.getChangeEncoding());

            payload.setBody(convertedBytes);
            payload.setCurrentEncoding(this.values.getChangeEncoding());

            log.info("Encoding converted: [{} -> {}] size: [{}] data: [{}]",
                    originalEncoding, this.values.getChangeEncoding(), payload.getBody().length,
                    new String(payload.getBody(), payload.getCurrentEncoding()));
        }
        return payload;
    }
}
