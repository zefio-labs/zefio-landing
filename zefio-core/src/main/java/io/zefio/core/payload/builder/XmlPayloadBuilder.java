package io.zefio.core.payload.builder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.CorrelationIdType;
import io.zefio.core.payload.builder.config.Telegram;
import io.zefio.core.payload.builder.config.XmlValues;
import org.apache.commons.lang3.ObjectUtils;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * Handles XML payload construction and parsing.
 * Utilizes XmlMapper for simple mapping and the standard XPath engine for precise
 * data extraction from hierarchical XML structures.
 */
public class XmlPayloadBuilder extends BasePayloadBuilder implements PayloadBuilder {
    private final XmlValues values;
    private final ObjectMapper mapper = new XmlMapper();

    public XmlPayloadBuilder(Telegram telegram){
        super(telegram);
        this.values = (XmlValues) telegram.getValues();
    }

    @Override
    public String extractCorrelationId(Object original, Object obj, Charset encoding) throws FlowException {
        String trxID = "";
        try {
            CorrelationIdType type = this.telegram.getValues().getCorrelation().getType();

            if (type == CorrelationIdType.Key || type == CorrelationIdType.XPath) {

                // Strategy 1: Simple 1-depth extraction using XmlMapper
                if (type == CorrelationIdType.Key) {
                    String key = values.getCorrelation().getKey();
                    JsonNode rootNode;
                    if (obj instanceof byte[]) {
                        rootNode = mapper.readTree(new String((byte[]) obj, encoding));
                    } else {
                        rootNode = mapper.valueToTree(obj);
                    }
                    JsonNode valNode = rootNode.path(key);
                    if (!valNode.isMissingNode()) trxID = valNode.asText();
                }
                // Strategy 2: Precision extraction using standard Java XPath engine
                else {
                    String path = values.getCorrelation().getPath();
                    byte[] xmlBytes = (obj instanceof byte[]) ? (byte[]) obj : mapper.writeValueAsBytes(obj);

                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    factory.setNamespaceAware(true);
                    DocumentBuilder docBuilder = factory.newDocumentBuilder();

                    ByteArrayInputStream bais = new ByteArrayInputStream(xmlBytes);
                    InputStreamReader reader = new InputStreamReader(bais, encoding);
                    InputSource inputSource = new InputSource(reader);

                    Document doc = docBuilder.parse(inputSource);

                    XPath xPath = XPathFactory.newInstance().newXPath();
                    trxID = xPath.compile(path).evaluate(doc);
                }

                if (ObjectUtils.isEmpty(trxID)) {
                    log.error("XML Correlation [{}] not found or empty",
                            type == CorrelationIdType.Key ? values.getCorrelation().getKey() : values.getCorrelation().getPath());
                    throw new FlowException(FlowResultStatus.MESSAGE_FORMAT_ERROR, "Required XML Correlation ID not found");
                }
                return trxID.trim();
            }
        } catch (FlowException e) {
            throw e;
        } catch (IOException e) {
            log.error("XML parsing failed during TrxID extraction", e);
            throw new FlowException(e, FlowResultStatus.MESSAGE_FORMAT_ERROR);
        } catch (Exception e) {
            log.error("Unexpected error in XmlPayloadBuilder", e);
            throw new FlowException(e, FlowResultStatus.MESSAGE_FORMAT_ERROR);
        }

        trxID = super.extractCorrelationIdByJms(original);
        if (ObjectUtils.isEmpty(trxID)) {
            throw new FlowException(FlowResultStatus.MESSAGE_FORMAT_ERROR, "Could not determine XML Transaction ID");
        }
        return trxID;
    }

    /**
     * Serializes XML string into a nested Map for SpEL navigation.
     * Example: <Header><Id>1</Id></Header> maps to map.get("Header").get("Id").
     */
    @Override
    public Map<String, Object> parseToMap(byte[] body, Charset encoding) throws Exception {
        if (body == null || body.length == 0) return java.util.Collections.emptyMap();
        return mapper.readValue(new String(body, encoding), new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Reassembles logical Map back into XML bytes.
     */
    @Override
    public byte[] buildFromMap(Map<String, Object> map, Charset encoding) throws Exception {
        if (map == null || map.isEmpty()) return new byte[0];
        return mapper.writeValueAsString(map).getBytes(encoding);
    }
}
