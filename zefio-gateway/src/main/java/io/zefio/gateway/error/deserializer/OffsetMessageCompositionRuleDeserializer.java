package io.zefio.gateway.error.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import io.zefio.gateway.error.dto.common.OffsetMessageCompositionRule;
import io.zefio.gateway.error.base.ErrorMessage;

import java.io.IOException;

public class OffsetMessageCompositionRuleDeserializer extends JsonDeserializer<OffsetMessageCompositionRule> {

    @Override
    public OffsetMessageCompositionRule deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);

        // Return null if the node is null or an empty string ("")
        if (node == null || node.isNull() || (node.isTextual() && node.asText().trim().isEmpty())) {
            return null;
        }

        OffsetMessageCompositionRule rule = new OffsetMessageCompositionRule();

        JsonNode offsetNode = node.get(OffsetMessageCompositionRule.Fields.OFFSET);
        if (offsetNode != null && offsetNode.isInt()) {
            rule.setOffset(offsetNode.asInt());
        }

        JsonNode modeNode = node.get(OffsetMessageCompositionRule.Fields.MODE);
        if (modeNode != null && modeNode.isTextual()) {
            String modeText = modeNode.asText();
            try {
                rule.setMode(ErrorMessage.valueOf(modeText));
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid mode value for ErrorMessage enum: " + modeText, e);
            }
        }

        return rule;
    }
}
