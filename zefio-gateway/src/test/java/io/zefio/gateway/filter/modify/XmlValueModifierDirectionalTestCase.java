package io.zefio.gateway.filter.modify;

import io.zefio.core.GatewayInterceptor;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ZefioMessage;
import io.zefio.testsupport.payload.IPayloadBuilderFactory;
import io.zefio.testsupport.payload.XmlPayloadBuilderFactory;
import io.zefio.testsupport.filter.AbstractNormalFilterTestCase;
import org.apache.commons.lang3.ObjectUtils;
import org.javatuples.Pair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("XmlValueModifierDirectional 필터 테스트")
public class XmlValueModifierDirectionalTestCase extends AbstractNormalFilterTestCase {

    public XmlValueModifierDirectionalTestCase() throws Exception {
        super("xml-insert-1");
    }

    @Override
    public IPayloadBuilderFactory createSenderBuilder() throws Exception {
        return XmlPayloadBuilderFactory.createStandardFactory("xml-key-test", filterEncoding, 500, "");
    }

    @Override
    public GatewayInterceptor createFilter(Map<String, Object> context) {
        return buildFilterWithContext(context);
    }

    // =========================================================================
    // 🛠️ [Helper 1] 필터 생성 보일러플레이트 제거
    // =========================================================================
    private GatewayInterceptor buildFilterWithContext(Map<String, Object> context) {
        PluginContext ctx = PluginContext.builder()
                .flowName("default")
                .pluginName(getClass().getName())
                .telegramName("xml-key-test") // 🚀 생성자 검증용 공통 주입
                .sharedScheduledPool(sharedPool.getValue0())
                .sharedIoPool(sharedPool.getValue1())
                .context(context)
                .build();
        return new ValueModifierDirectional(ctx);
    }

    // =========================================================================
    // 🛠️ [Helper 2] 필터 재기동(Re-initialize) 헬퍼
    // =========================================================================
    private void reInitializeFilter(String yamlKey) throws Exception {
        Map<String, Object> context = getContext(yamlKey);
        this.filterEncoding = ObjectUtils.isEmpty(context.get("requestEncoding")) ?
                StandardCharsets.UTF_8 : Charset.forName(context.get("requestEncoding").toString());
        this.filter = buildFilterWithContext(context);
        this.filter.initialise();
    }

    // =========================================================================
    // 🛠️ [Helper 3] 이벤트 생성 보일러플레이트 제거
    // =========================================================================
    private Payload createXmlEvent(String xmlBody, String trxId) {
        byte[] bodyBytes = xmlBody != null ? xmlBody.getBytes(filterEncoding) : null;
        Payload payload = new ZefioMessage(bodyBytes, filterEncoding);
        payload.setTelegramName("xml-key-test"); // 🚀 팩토리 조회용 공통 주입
        payload.setTrxID(trxId);
        return payload;
    }

    // =========================================================================
    // 🧪 테스트 케이스 시작 (반복 코드 완벽 제거)
    // =========================================================================

    @Test
    @DisplayName("XML body에 key 데이터 삽입")
    public void testXmlInsert() throws Exception {
        String xml = "<user><name>XXX</name><age>30</age></user>";
        Payload payload = createXmlEvent(xml, "trx001");
        payload.setHeader("userName", "Alice");

        filter.executeAsync(payload, Executors.newSingleThreadExecutor()).join();

        // XML 비교
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document expectedDoc = dbf.newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream("<user><name>Alice</name><age>30</age></user>".getBytes(filterEncoding)));
        Document actualDoc = dbf.newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(payload.getBody()));

        assertEquals(expectedDoc.getDocumentElement().getElementsByTagName("name").item(0).getTextContent(),
                actualDoc.getDocumentElement().getElementsByTagName("name").item(0).getTextContent());
        assertEquals(expectedDoc.getDocumentElement().getElementsByTagName("age").item(0).getTextContent(),
                actualDoc.getDocumentElement().getElementsByTagName("age").item(0).getTextContent());

        assertEquals("Alice", payload.getHeader("userName"));
    }

    @Test
    @DisplayName("누적 병합")
    public void testXmlMultipleChildren() throws Exception {
        reInitializeFilter("xml-insert-2");

        String xml = "<data><key1></key1><key2></key2></data>";
        Payload payload = createXmlEvent(xml, "trx002");
        payload.setHeader("key1", "A");
        payload.setHeader("key2", "B");

        filter.executeAsync(payload, Executors.newSingleThreadExecutor()).join();

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document expectedDoc = dbf.newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream("<data><key1>A</key1><key2>B</key2></data>".getBytes(filterEncoding)));
        Document actualDoc = dbf.newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(payload.getBody()));

        assertEquals(expectedDoc.getDocumentElement().getElementsByTagName("key1").item(0).getTextContent(),
                actualDoc.getDocumentElement().getElementsByTagName("key1").item(0).getTextContent());
        assertEquals(expectedDoc.getDocumentElement().getElementsByTagName("key2").item(0).getTextContent(),
                actualDoc.getDocumentElement().getElementsByTagName("key2").item(0).getTextContent());

        assertEquals("A", payload.getHeader("key1"));
        assertEquals("B", payload.getHeader("key2"));
    }

    @Test
    @DisplayName("XML 노드 추출 후 body에서 제거")
    public void testExtractAndRemoveFromBody() throws Exception {
        reInitializeFilter("xmlKeyExtractor1");

        String xml = "<root><header>ABC</header><body>DEF</body></root>";
        Payload requestPayload = createXmlEvent(xml, "trxXml001");

        List<Pair<String, String>> propertyPairs = Collections.singletonList(Pair.with("header", "ABC"));

        filter.executeAsync(requestPayload, Executors.newSingleThreadExecutor()).join();

        // body XML 비교
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document expectedDoc = dbf.newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream("<root><body>DEF</body></root>".getBytes(filterEncoding)));
        Document actualDoc = dbf.newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(requestPayload.getBody()));

        assertEquals(expectedDoc.getDocumentElement().getElementsByTagName("body").item(0).getTextContent(),
                actualDoc.getDocumentElement().getElementsByTagName("body").item(0).getTextContent());

        // property값 체크
        for (Pair<String, String> propertyPair : propertyPairs) {
            String extractedPropertyValue = (String) requestPayload.getHeader(propertyPair.getValue0());
            assertNotNull(extractedPropertyValue);
            assertEquals(propertyPair.getValue1(), extractedPropertyValue);
        }
    }

    @Test
    @DisplayName("removeExtracted=false 옵션일 때 body가 변경되지 않는지 확인")
    public void testExtractWithoutRemovingBody() throws Exception {
        reInitializeFilter("xmlKeyExtractor2");

        String xml = "<root><header>ABC</header><body>DEF</body></root>";
        Payload requestPayload = createXmlEvent(xml, "trxXml002");

        filter.executeAsync(requestPayload, Executors.newSingleThreadExecutor()).join();

        assertEquals("ABC", requestPayload.getHeader("header"));

        // XML 구조 비교
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document expectedDoc = dbf.newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(xml.getBytes(filterEncoding)));
        Document actualDoc = dbf.newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(requestPayload.getBody()));

        assertEquals(expectedDoc.getDocumentElement().getNodeName(), actualDoc.getDocumentElement().getNodeName());
        assertEquals(expectedDoc.getDocumentElement().getElementsByTagName("header").item(0).getTextContent(),
                actualDoc.getDocumentElement().getElementsByTagName("header").item(0).getTextContent());
        assertEquals(expectedDoc.getDocumentElement().getElementsByTagName("body").item(0).getTextContent(),
                actualDoc.getDocumentElement().getElementsByTagName("body").item(0).getTextContent());

        filter.close();
    }

    @Test
    @DisplayName("XML 값 치환")
    public void testXmlReplace() throws Exception {
        reInitializeFilter("xmlModifier1");

        String xmlBody = "<person><name>Bob</name><age>30</age></person>";
        Payload requestPayload = createXmlEvent(xmlBody, "abc");

        filter.executeAsync(requestPayload, Executors.newSingleThreadExecutor()).join();
        String result = new String(requestPayload.getBody(), filterEncoding);

        assertTrue(result.contains("<name>Alice</name>"));
        assertTrue(result.contains("<age>30</age>"));
    }
}
