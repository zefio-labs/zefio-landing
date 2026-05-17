package io.zefio.gateway.error.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.gateway.error.dto.XmlFaultValues;
import io.zefio.gateway.error.dto.common.KeyedErrorCodeReplacementRule;
import io.zefio.gateway.error.dto.common.KeyedMessageCompositionRule;
import io.zefio.gateway.error.dto.common.KeyedValueOverride;
import io.zefio.gateway.error.util.ErrorUtils;
import io.zefio.core.payload.Payload;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/**
 * Modifies XML payloads for error reporting.
 * Retains the original root element name, applies key-based tag substitutions, and serializes back to XML.
 */
public class XmlErrorEditor implements ErrorMessageEditor {
    private final XmlMapper mapper = new XmlMapper();
    private final List<KeyedValueOverride> valueOverrides;
    private final List<KeyedErrorCodeReplacementRule> errorCodeRules;
    private final KeyedMessageCompositionRule messageRule;

    public XmlErrorEditor(XmlFaultValues config) {
        this.valueOverrides = config.getValueOverrides();
        this.errorCodeRules = config.getErrorCodeRules();
        this.messageRule = config.getMessageRule();
    }

    @Override
    public byte[] edit(Payload payload, Charset encoding, Throwable throwable) throws Exception {
        // 1. Extract the original XML root element name
        String rootElementName = extractRootElementName(payload.getBody());

        // 2. Parse XML into JsonNode
        JsonNode rootNode = mapper.readTree(payload.getBody());

        // 3. Cast to ObjectNode
        if (!(rootNode instanceof ObjectNode)) {
            throw new FlowException(FlowResultStatus.MESSAGE_FORMAT_ERROR, "Root of XML must be an object");
        }
        ObjectNode objectNode = (ObjectNode) rootNode;

        // 4. Merge message composition prefix map
        Map<String, Object> prefixMap = ErrorUtils.mappingMessageComposition(messageRule, payload, encoding, throwable);
        for (Map.Entry<String, Object> entry : prefixMap.entrySet()) {
            objectNode.put(entry.getKey(), String.valueOf(entry.getValue()));
        }

        // 5. Apply error code replacements
        ErrorUtils.mappingKeyedErrorCode(objectNode, errorCodeRules, throwable);

        // 6. Apply fixed key value overrides
        for (KeyedValueOverride kv : valueOverrides) {
            objectNode.put(kv.getKey(), kv.getValue());
        }

        // 7. Serialize back to XML bytes with original root name
        return mapper
                .writerWithDefaultPrettyPrinter()
                .withRootName(rootElementName)
                .writeValueAsBytes(objectNode);
    }

    private String extractRootElementName(byte[] xmlBytes) throws Exception {
        try (InputStream is = new ByteArrayInputStream(xmlBytes)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setIgnoringComments(true);
            factory.setIgnoringElementContentWhitespace(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(is);
            return document.getDocumentElement().getNodeName();
        }
    }
}
