package io.zefio.gateway.filter.modify.delegate;

import io.zefio.core.payload.Payload;
import io.zefio.core.payload.util.TransferUtils;
import io.zefio.gateway.filter.modify.dto.XmlValueModifierChild;
import io.zefio.gateway.filter.modify.dto.XmlValueModifierDelegateValues;
import io.zefio.gateway.filter.modify.ValueModifierDirectional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Handles modifications for XML payloads using XPath for node selection and DOM for manipulation.
 */
public class XmlValueModifierDelegate implements ValueModifierDelegate {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final XmlValueModifierDelegateValues config;

    public XmlValueModifierDelegate(XmlValueModifierDelegateValues config) {
        this.config = config;
    }

    @Override
    public void modify(Payload payload, List<?> children, Charset encoding, ValueModifierDirectional parentInstance) throws Exception {
        String xml = new String(payload.getBody(), encoding);
        log.info("Starting XML modification. Direction: {}, Children count: {}", config.getDirection(), children.size());

        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));

        XPath xpath = XPathFactory.newInstance().newXPath();

        for (Object obj : children) {
            XmlValueModifierChild child = (XmlValueModifierChild) obj;
            log.debug("Processing child xpath [{}], valueOrProperty [{}]", child.getXpath(), child.getValueOrProperty());

            switch (config.getDirection()) {
                case MODIFY_BODY:
                    Node node = (Node) xpath.evaluate(child.getXpath(), doc, XPathConstants.NODE);
                    if (node != null) {
                        String newValue = TransferUtils.formConvertor(child.getValueOrProperty(), payload);
                        node.setTextContent(newValue);
                        log.info("MODIFY_BODY: Replaced XML node [{}] value", child.getXpath());
                    } else {
                        log.warn("MODIFY_BODY: Node not found for xpath [{}]", child.getXpath());
                    }
                    break;

                case PROPERTY_TO_BODY:
                    Object prop = payload.getHeader(child.getValueOrProperty());
                    if (prop == null) {
                        log.warn("PROPERTY_TO_BODY: Source property [{}] is null, skipping", child.getValueOrProperty());
                        continue;
                    }

                    Node target = (Node) xpath.evaluate(child.getXpath(), doc, XPathConstants.NODE);
                    if (target != null) {
                        target.setTextContent(prop.toString());
                        log.info("PROPERTY_TO_BODY: Updated existing node [{}]", child.getXpath());
                        break;
                    }

                    String elementName = sanitizeElementName(child.getElementName());
                    Element newEl = doc.createElement(elementName);
                    newEl.setTextContent(prop.toString());

                    Node parentNode = doc.getDocumentElement();
                    if (child.getParentXpath() != null && !child.getParentXpath().isEmpty()) {
                        Node p = (Node) xpath.evaluate(child.getParentXpath(), doc, XPathConstants.NODE);
                        if (p != null) parentNode = p;
                    }

                    parentNode.appendChild(newEl);
                    log.info("PROPERTY_TO_BODY: Inserted new element [{}] under [{}]", elementName, child.getParentXpath());
                    break;

                case BODY_TO_PROPERTY:
                    String extractedVal = xpath.evaluate(child.getXpath(), doc);
                    payload.setHeader(child.getValueOrProperty(), extractedVal);
                    log.info("BODY_TO_PROPERTY: Extracted value from xpath [{}] to property [{}]", child.getXpath(), child.getValueOrProperty());

                    if (config.getRemoveExtracted()) {
                        Node removeNode = (Node) xpath.evaluate(child.getXpath(), doc, XPathConstants.NODE);
                        if (removeNode != null && removeNode.getParentNode() != null) {
                            removeNode.getParentNode().removeChild(removeNode);
                            log.info("BODY_TO_PROPERTY: Removed node [{}] after extraction", child.getXpath());
                        }
                    }
                    break;
            }
        }

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, encoding.name());
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));

        payload.setBody(writer.toString().getBytes(encoding));
        log.info("XML body update complete");
    }

    private String sanitizeElementName(String name) {
        if (name == null || name.isEmpty()) return "element";
        char first = name.charAt(0);
        if (!Character.isLetter(first) && first != '_') name = "_" + name;
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
