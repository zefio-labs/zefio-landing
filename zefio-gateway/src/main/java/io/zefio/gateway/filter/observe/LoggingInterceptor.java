package io.zefio.gateway.filter.observe;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.BaseComputeInterceptor;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;

import java.nio.charset.Charset;

/**
 * Observation filter for logging transaction details.
 * Provides visibility into payload headers, metadata, and body content for debugging and auditing.
 */
public class LoggingInterceptor extends BaseComputeInterceptor {

    public LoggingInterceptor(PluginContext context) {
        super(context);
    }

    @Override
    public String getDescription() {
        return "Filter for processing transaction logging.";
    }

    @Override
    public Payload process(Payload payload) throws FlowException {
        Charset encoding = payload.getCurrentEncoding();
        byte[] body = payload.getBody();

        // Performance Optimization: Perform string assembly only if INFO level is enabled
        if (log.isInfoEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n------------------------------------------------------\n");
            sb.append(" [Logging Interceptor] - ").append(this.pluginName).append("\n");
            sb.append("------------------------------------------------------\n");
            sb.append(" Transaction ID : ").append(payload.getTrxID()).append("\n");
            sb.append(" Telegram Name  : ").append(payload.getTelegramName()).append("\n");
            sb.append(" Encoding       : ").append(encoding).append("\n");

            // 1. Headers (HTTP Headers and System Metadata)
            sb.append("------------------------------------------------------\n");
            sb.append(" Payload Headers & Metadata:\n");
            if (payload.getHeaders() != null && !payload.getHeaders().isEmpty()) {
                payload.getHeaders().forEach((key, value) ->
                        sb.append(String.format("   - %-30s : %s\n", key, value))
                );
            } else {
                sb.append("   (No properties found)\n");
            }

            // 2. Body Content
            sb.append("------------------------------------------------------\n");
            sb.append(" Body Content (Length: ").append(body != null ? body.length : 0).append(" bytes):\n");
            if (body != null && body.length > 0) {
                sb.append("[").append(new String(body, encoding)).append("]\n");
            } else {
                sb.append("[Empty Body]\n");
            }
            sb.append("------------------------------------------------------");

            log.info(sb.toString());
        }
        return payload;
    }
}
