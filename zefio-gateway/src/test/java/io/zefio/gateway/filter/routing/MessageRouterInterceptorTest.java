package io.zefio.gateway.filter.routing;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.GatewayInterceptor;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.ZefioMessage;
import io.zefio.core.payload.builder.config.*;
import io.zefio.core.payload.util.TelegramFactory;
import io.zefio.gateway.filter.routing.dto.MessageRoutingRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageRouterInterceptorTest {

    @Mock private PluginContext context;
    @Mock private Payload mockPayload;
    @Mock private PayloadBuilder mockFixedBuilder;
    @Mock private PayloadBuilder mockJsonBuilder;
    @Mock private PayloadBuilder mockXmlBuilder;

    @Mock private GatewayInterceptor targetFilter1;
    @Mock private GatewayInterceptor targetFilter2;

    private MessageRouterInterceptor routerFilter;
    private ExecutorService flowExecutor;


    @BeforeEach
    void setUp() throws Exception {
        MDC.clear();
        flowExecutor = Executors.newSingleThreadExecutor();
        TelegramFactory.clear();

        // 포맷별 Mock 빌더 셋업 및 팩토리 등록
        setupMockBuilder(mockFixedBuilder, "fixed-tg", Telegram.Type.Fixed, new FixedValues());
        setupMockBuilder(mockJsonBuilder, "json-tg", Telegram.Type.JSON, new JsonValues());
        setupMockBuilder(mockXmlBuilder, "xml-tg", Telegram.Type.XML, new XmlValues());

        // 타겟 필터 기본 동작 세팅
        when(targetFilter1.getPluginName()).thenReturn("target1");
        when(targetFilter2.getPluginName()).thenReturn("target2");
        when(targetFilter1.executeAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(mockPayload));
        when(targetFilter2.executeAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(mockPayload));

        // 🚀 [삭제] BytesUtils를 모킹하던 거추장스러운 로직 삭제! (실제 유틸리티 메서드를 타도록 내버려 둠)
    }

    @AfterEach
    void tearDown() {
        flowExecutor.shutdownNow();
    }

    // =========================================================================
    // 🛠️ Helpers
    // =========================================================================
    private void setupMockBuilder(PayloadBuilder builder, String name, Telegram.Type type, TelegramValues values) {
        Telegram tg = mock(Telegram.class);
        when(tg.getName()).thenReturn(name);
        when(tg.getType()).thenReturn(type);
        when(tg.getValues()).thenReturn(values);
        when(builder.getTelegram()).thenReturn(tg);
        TelegramFactory.register(name, builder);
    }

    private void initRouter(String telegramName, MessageRoutingRule... rules) throws Exception {
        Map<String, Object> ctxMap = new HashMap<>();
        ctxMap.put("routingRules", Arrays.asList(rules));

        when(context.getFlowName()).thenReturn("router-flow");
        when(context.getTelegramName()).thenReturn(telegramName);
        when(context.getContext()).thenReturn(ctxMap);

        routerFilter = new MessageRouterInterceptor(context);

        Map<String, GatewayInterceptor> toolMap = new HashMap<>();
        toolMap.put("target1", targetFilter1);
        toolMap.put("target2", targetFilter2);

        Field mapField = MessageRouterInterceptor.class.getDeclaredField("toolModuleMap");
        mapField.setAccessible(true);
        mapField.set(routerFilter, toolMap);
    }

    private Payload createEvent(String telegramName, String bodyStr) {
        byte[] body = (bodyStr == null) ? null : bodyStr.getBytes(StandardCharsets.UTF_8);
        Payload payload = new ZefioMessage(body, StandardCharsets.UTF_8);
        payload.setTelegramName(telegramName);
        return payload;
    }

    private FlowException catchEx(CompletableFuture<Payload> future) {
        CompletionException thrown = assertThrows(CompletionException.class, future::join);
        return (FlowException) thrown.getCause();
    }

    // =========================================================================
    // [1] Fixed 포맷 모든 시나리오
    // =========================================================================

    @Test @DisplayName("Fixed 정상: Offset/Length 기반 추출")
    void testFixed_Success() throws Exception {
        MessageRoutingRule r = new MessageRoutingRule();
        r.setOffset(4); r.setLength(2); r.setMatchValue("OK"); r.setRefModuleName("target1");
        initRouter("fixed-tg", r);
        Payload e = createEvent("fixed-tg", "1234OK789");
        assertNotNull(routerFilter.executeAsync(e, flowExecutor).join());
        verify(targetFilter1).executeAsync(e, flowExecutor);
    }

    @Test @DisplayName("Fixed 방어: 전문 길이 부족 시 BAD_REQUEST")
    void testFixed_ShortBody() throws Exception {
        MessageRoutingRule r = new MessageRoutingRule();
        r.setOffset(10); r.setLength(5); r.setMatchValue("OK"); r.setRefModuleName("target1");
        initRouter("fixed-tg", r);
        assertEquals(FlowResultStatus.BAD_REQUEST, catchEx(routerFilter.executeAsync(createEvent("fixed-tg", "SHORT"), flowExecutor)).getStatus());
    }

    // =========================================================================
    // [2] JSON 포맷 모든 시나리오
    // =========================================================================

    @Test @DisplayName("JSON 정상: 1Depth Key 기반")
    void testJson_Key_Success() throws Exception {
        MessageRoutingRule r = new MessageRoutingRule();
        r.setKey("type"); r.setMatchValue("REQ"); r.setRefModuleName("target1");
        initRouter("json-tg", r);
        assertNotNull(routerFilter.executeAsync(createEvent("json-tg", "{\"type\":\"REQ\"}"), flowExecutor).join());
    }

    @Test @DisplayName("JSON 정상: JsonPath 중첩 구조")
    void testJson_Path_Success() throws Exception {
        MessageRoutingRule r = new MessageRoutingRule();
        r.setPath("$.header.code"); r.setMatchValue("200"); r.setRefModuleName("target2");
        initRouter("json-tg", r);
        assertNotNull(routerFilter.executeAsync(createEvent("json-tg", "{\"header\":{\"code\":\"200\"}}"), flowExecutor).join());
    }

    @Test @DisplayName("JSON Multi-Depth: Jayway JsonPath 정밀 추출")
    void testJson_DeepNested() throws Exception {
        MessageRoutingRule r = new MessageRoutingRule();
        r.setPath("$.header.routing.info.target"); r.setMatchValue("OQ"); r.setRefModuleName("target1");
        initRouter("json-tg", r);
        assertNotNull(routerFilter.executeAsync(createEvent("json-tg", "{\"header\":{\"routing\":{\"info\":{\"target\":\"OQ\"}}}}"), flowExecutor).join());
    }

    @Test @DisplayName("JSON Array Depth: 배열 인덱스 추출")
    void testJson_Array() throws Exception {
        MessageRoutingRule r = new MessageRoutingRule();
        r.setPath("$.body.items[1].status"); r.setMatchValue("ACTIVE"); r.setRefModuleName("target2");
        initRouter("json-tg", r);
        assertNotNull(routerFilter.executeAsync(createEvent("json-tg", "{\"body\":{\"items\":[{\"status\":\"IDLE\"},{\"status\":\"ACTIVE\"}]}}"), flowExecutor).join());
    }

    @Test @DisplayName("JSON 방어: 포맷 깨짐 시 BAD_REQUEST")
    void testJson_InvalidFormat() throws Exception {
        initRouter("json-tg", new MessageRoutingRule());
        assertEquals(FlowResultStatus.BAD_REQUEST, catchEx(routerFilter.executeAsync(createEvent("json-tg", "{ invalid"), flowExecutor)).getStatus());
    }

    // =========================================================================
    // [3] XML 포맷 모든 시나리오
    // =========================================================================

    @Test @DisplayName("XML 정상: 1Depth 노드")
    void testXml_Key_Success() throws Exception {
        MessageRoutingRule r = new MessageRoutingRule();
        r.setKey("command"); r.setMatchValue("START"); r.setRefModuleName("target1");
        initRouter("xml-tg", r);
        assertNotNull(routerFilter.executeAsync(createEvent("xml-tg", "<root><command>START</command></root>"), flowExecutor).join());
    }

    @Test @DisplayName("XML 정상: XPath 중첩 구조")
    void testXml_XPath_Success() throws Exception {
        MessageRoutingRule r = new MessageRoutingRule();
        r.setPath("//body/item/status"); r.setMatchValue("ACTIVE"); r.setRefModuleName("target2");
        initRouter("xml-tg", r);
        assertNotNull(routerFilter.executeAsync(createEvent("xml-tg", "<msg><body><item><status>ACTIVE</status></item></body></msg>"), flowExecutor).join());
    }

    @Test @DisplayName("XML Attribute: XPath 속성값 추출")
    void testXml_Attribute() throws Exception {
        MessageRoutingRule r = new MessageRoutingRule();
        r.setPath("//item[@id='002']/@status"); r.setMatchValue("SUCCESS"); r.setRefModuleName("target2");
        initRouter("xml-tg", r);
        assertNotNull(routerFilter.executeAsync(createEvent("xml-tg", "<data><item id='002' status='SUCCESS'/></data>"), flowExecutor).join());
    }

    @Test @DisplayName("XML 방어: 포맷 깨짐 시 BAD_REQUEST")
    void testXml_InvalidFormat() throws Exception {
        initRouter("xml-tg", new MessageRoutingRule());
        assertEquals(FlowResultStatus.BAD_REQUEST, catchEx(routerFilter.executeAsync(createEvent("xml-tg", "<msg><status>ACTIVE</msg>"), flowExecutor)).getStatus());
    }

    // =========================================================================
    // [4] 라우팅 논리 및 예외 전이
    // =========================================================================

    @Test @DisplayName("다중 룰 (Priority): Fallback 성공")
    void testRouting_PriorityFallback() throws Exception {
        MessageRoutingRule r1 = new MessageRoutingRule(); r1.setKey("type"); r1.setMatchValue("A"); r1.setRefModuleName("target1");
        MessageRoutingRule r2 = new MessageRoutingRule(); r2.setKey("type"); r2.setMatchValue("B"); r2.setRefModuleName("target2");
        initRouter("json-tg", r1, r2);
        assertNotNull(routerFilter.executeAsync(createEvent("json-tg", "{\"type\":\"B\"}"), flowExecutor).join());
        verify(targetFilter2).executeAsync(any(), any());
    }

    @Test @DisplayName("매칭 실패: 어떤 룰과도 일치하지 않을 때")
    void testRouting_NoMatch() throws Exception {
        initRouter("json-tg", new MessageRoutingRule());
        assertEquals(FlowResultStatus.BAD_REQUEST, catchEx(routerFilter.executeAsync(createEvent("json-tg", "{\"x\":\"y\"}"), flowExecutor)).getStatus());
    }

    @Test @DisplayName("타겟 필터 에러 역류: 하위 에러를 그대로 상위로")
    void testRouting_TargetFilterThrowsException() throws Exception {
        MessageRoutingRule r = new MessageRoutingRule(); r.setKey("type"); r.setMatchValue("A"); r.setRefModuleName("target1");
        initRouter("json-tg", r);
        CompletableFuture<Payload> failed = new CompletableFuture<>();
        failed.completeExceptionally(new FlowException(FlowResultStatus.DATABASE_TIMEOUT, "Target Dead"));
        when(targetFilter1.executeAsync(any(), any())).thenReturn(failed);
        assertEquals(FlowResultStatus.DATABASE_TIMEOUT, catchEx(routerFilter.executeAsync(createEvent("json-tg", "{\"type\":\"A\"}"), flowExecutor)).getStatus());
    }

    // =========================================================================
    // [5] 극한 방어 및 Edge Cases
    // =========================================================================

    @Test @DisplayName("극한 방어: Telegram Values 자체가 Null")
    void testEdge_NullTelegramValues() throws Exception {
        setupMockBuilder(mockJsonBuilder, "null-values-tg", Telegram.Type.JSON, null);
        initRouter("null-values-tg", new MessageRoutingRule());
        assertEquals(FlowResultStatus.BAD_REQUEST, catchEx(routerFilter.executeAsync(createEvent("null-values-tg", "{}"), flowExecutor)).getStatus());
    }

    @Test @DisplayName("극한 방어: Body가 Null일 때")
    void testEdge_NullBody() throws Exception {
        initRouter("fixed-tg", new MessageRoutingRule());
        assertEquals(FlowResultStatus.BAD_REQUEST, catchEx(routerFilter.executeAsync(createEvent("fixed-tg", null), flowExecutor)).getStatus());
    }

    @Test @DisplayName("설정 엇갈림 방어: JSON 데이터에 Fixed 룰 적용")
    void testEdge_MismatchedConfig() throws Exception {
        MessageRoutingRule r = new MessageRoutingRule(); r.setOffset(0); r.setLength(2); r.setMatchValue("A"); r.setRefModuleName("target1");
        initRouter("json-tg", r);
        assertEquals(FlowResultStatus.BAD_REQUEST, catchEx(routerFilter.executeAsync(createEvent("json-tg", "{\"k\":\"v\"}"), flowExecutor)).getStatus());
    }
}
