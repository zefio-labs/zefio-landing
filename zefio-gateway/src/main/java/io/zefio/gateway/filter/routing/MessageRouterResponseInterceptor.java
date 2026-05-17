package io.zefio.gateway.filter.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.BaseComputeInterceptor;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;

import java.nio.charset.Charset;

/**
 * Interceptor that standardizes the final response by wrapping the original
 * body into a unified JSON structure.
 */
public class MessageRouterResponseInterceptor extends BaseComputeInterceptor {

    private final ObjectMapper mapper = new ObjectMapper();

    public MessageRouterResponseInterceptor(PluginContext context) {
        super(context);
    }

    @Override
    public String getDescription() {
        return "Standardizes the final response format with result status and original content.";
    }

    @Override
    public Payload process(Payload payload) throws FlowException {
        Charset encoding = payload.getCurrentEncoding();
        String responseBody = new String(payload.getBody(), encoding);

        ObjectNode finalResponse = mapper.createObjectNode();
        JsonNode originalNode = null;

        try {
            originalNode = mapper.readTree(responseBody);
            finalResponse.put("result", "success");
        } catch (Exception e) {
            // Handle cases where the body is not valid JSON or is empty
            finalResponse.put("result", "failure");
            finalResponse.put("message", "Invalid or malformed response body");
        }

        // Attach the original content to the standardized wrapper
        finalResponse.set("original", originalNode != null ? originalNode : mapper.createObjectNode());

        if (log.isInfoEnabled()) {
            log.info("Final standardized response generated:\n{}", finalResponse.toPrettyString());
        }

        byte[] newBody = finalResponse.toString().getBytes(encoding);
        payload.setBody(newBody);
        return payload;
    }
}
