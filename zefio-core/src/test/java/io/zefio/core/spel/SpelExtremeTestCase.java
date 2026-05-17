package io.zefio.core.spel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.builder.config.Telegram;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.ZefioMessage;
import io.zefio.core.payload.spel.PayloadExpressionEvaluator;
import io.zefio.core.payload.util.TelegramFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SpelExtremeTestCase {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Mock private PayloadBuilder mockJsonBuilder;
    @Mock private PayloadBuilder mockXmlBuilder;
    @Mock private PayloadBuilder mockFixedBuilder;

    private Payload jsonPayload;
    private Payload xmlPayload;
    private Payload fixedPayload;

    @BeforeEach
    void setUp() throws Exception {
        // =====================================================================
        // 1. Map for setting common properties (bypassing ConcurrentHashMap constraints)
        // =====================================================================
        Map<String, Object> commonProps = new HashMap<>();
        commonProps.put("retryCount", 2);
        commonProps.put("isAdmin", true);
        commonProps.put("threshold", 50.5);
        commonProps.put("strList", Arrays.asList("APPLE", "BANANA", "AVOCADO"));
        commonProps.put("legacy.system.key", "LEGACY_VAL");
        commonProps.put("key with space", "SPACE_VAL");
        commonProps.put("bizDate", "20260402");
        commonProps.put("hexVal", "0xFF");
        commonProps.put("emptyList", new ArrayList<>());
        commonProps.put("numList", Arrays.asList(10, 20, 30, 40, 50));

        Map<String, String> mdc = new HashMap<>();
        mdc.put("traceId", "MDC-9999");

        // =====================================================================
        // Create Mock Telegram objects since the constructor is private.
        // =====================================================================
        Telegram jsonTg = org.mockito.Mockito.mock(Telegram.class);
        when(jsonTg.getName()).thenReturn("mock-json");
        when(jsonTg.getType()).thenReturn(Telegram.Type.JSON);
        when(mockJsonBuilder.getTelegram()).thenReturn(jsonTg);

        Telegram xmlTg = org.mockito.Mockito.mock(Telegram.class);
        when(xmlTg.getName()).thenReturn("mock-xml");
        when(xmlTg.getType()).thenReturn(Telegram.Type.XML);
        when(mockXmlBuilder.getTelegram()).thenReturn(xmlTg);

        Telegram fixedTg = org.mockito.Mockito.mock(Telegram.class);
        when(fixedTg.getName()).thenReturn("mock-fixed");
        when(fixedTg.getType()).thenReturn(Telegram.Type.Fixed);
        when(mockFixedBuilder.getTelegram()).thenReturn(fixedTg);

        // =====================================================================
        // Initialize factory and globally register Mock builders.
        // =====================================================================
        TelegramFactory.clear();
        TelegramFactory.register("mock-json", mockJsonBuilder);
        TelegramFactory.register("mock-xml", mockXmlBuilder);
        TelegramFactory.register("mock-fixed", mockFixedBuilder);

        // =====================================================================
        // 2. Prepare Mock JSON data
        // =====================================================================
        String jsonPayloadStr = "{\"header\":{\"trxId\":\"T123\",\"bankCode\":\"003\"},\"payload\":{\"items\":[{\"id\":1,\"price\":100},{\"id\":2,\"price\":250}],\"isVip\":true, \"status\":null}}";
        this.jsonPayload = new ZefioMessage(jsonPayloadStr.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        this.jsonPayload.setTrxID("MOCK-TRX-ID");
        this.jsonPayload.setTelegramName("mock-json");
        commonProps.forEach(this.jsonPayload::setHeader);
        this.jsonPayload.setMdcContext(mdc);

        Map<String, Object> jsonMap = mapper.readValue(jsonPayloadStr, new TypeReference<Map<String, Object>>() {});
        when(mockJsonBuilder.parseToMap(any(), any())).thenReturn(jsonMap);

        // =====================================================================
        // 3. Prepare Mock XML data
        // =====================================================================
        String xmlPayloadStr = "<MSG><Header><GlobalId>X999</GlobalId></Header><Body><Customer type=\"VIP\"><Age>35</Age><Name>Tobby</Name></Customer></Body></MSG>";
        this.xmlPayload = new ZefioMessage(xmlPayloadStr.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        this.xmlPayload.setTelegramName("mock-xml");
        commonProps.forEach(this.xmlPayload::setHeader);
        this.xmlPayload.setMdcContext(mdc);

        Map<String, Object> xmlMap = new HashMap<>();
        xmlMap.put("Header", Collections.singletonMap("GlobalId", "X999"));
        Map<String, Object> customer = new HashMap<>();
        customer.put("Age", "35");
        customer.put("Name", "Tobby");
        customer.put("_type", "VIP");
        xmlMap.put("Body", Collections.singletonMap("Customer", customer));
        when(mockXmlBuilder.parseToMap(any(), any())).thenReturn(xmlMap);

        // =====================================================================
        // 4. Prepare Mock FIXED data
        // =====================================================================
        String fixedPayloadStr = "003REQ000100000        Tobby20260402TRX";
        this.fixedPayload = new ZefioMessage(fixedPayloadStr.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        this.fixedPayload.setTelegramName("mock-fixed");
        commonProps.forEach(this.fixedPayload::setHeader);
        this.fixedPayload.setMdcContext(mdc);

        Map<String, Object> fixedMap = new HashMap<>();
        fixedMap.put("BANK_CODE", "003");
        fixedMap.put("REQ_TYPE", "REQ");
        fixedMap.put("AMT", "000100000");
        fixedMap.put("NAME", "Tobby");
        fixedMap.put("DATE", "20260402");
        fixedMap.put("TAIL", "TRX");
        when(mockFixedBuilder.parseToMap(any(), any())).thenReturn(fixedMap);
    }

    private void assertSpel(String expression, Payload payload, Class<?> expectedType, Object expectedValue) {
        Object result = PayloadExpressionEvaluator.evaluate(expression, payload, expectedType);
        assertEquals(expectedValue, result, "SpEL evaluation failed: " + expression);
    }

    // =====================================================================
    // 100 Scenarios
    // =====================================================================

    @Nested
    @DisplayName("[Group A] Core Event object metadata access")
    class CoreEventTests {
        @Test void testEventMetadata() {
            assertSpel("#{payload.trxID}", jsonPayload, String.class, "MOCK-TRX-ID");
            assertSpel("#{payload.currentEncoding.name()}", jsonPayload, String.class, "UTF-8");
            assertSpel("#{payload.queueWaitTime}", jsonPayload, Long.class, 0L);
            assertSpel("#{!payload.suppressStatLog}", jsonPayload, Boolean.class, true);
            assertSpel("#{new java.text.SimpleDateFormat('yyyyMMdd').format(payload.requestTime)}", jsonPayload, String.class, new SimpleDateFormat("yyyyMMdd").format(new Date()));
        }
    }

    @Nested
    @DisplayName("[Group B] Event Properties operations")
    class PropertyTests {
        @Test void testProperties() {
            assertSpel("#{payload.headers.retryCount}", jsonPayload, Integer.class, 2);
            assertSpel("#{payload.headers.retryCount * 10}", jsonPayload, Integer.class, 20);
            assertSpel("#{payload.headers.isAdmin and payload.headers.retryCount < 5}", jsonPayload, Boolean.class, true);
            assertSpel("#{payload.headers.threshold + 10.0}", jsonPayload, Double.class, 60.5);
            assertSpel("#{payload.headers['missingVal'] ?: 'DEFAULT_CODE'}", jsonPayload, String.class, "DEFAULT_CODE");
        }
    }

    @Nested
    @DisplayName("[Group C] Deep JSON Body traversal (Lazy Parsing)")
    class JsonBodyTests {
        @Test void testJsonBody() {
            assertSpel("#{body.header.bankCode}", jsonPayload, String.class, "003");
            assertSpel("#{body.payload.items[0].price}", jsonPayload, Integer.class, 100);
            assertSpel("#{body.payload.items[0].price + body.payload.items[1].price}", jsonPayload, Integer.class, 350);
            assertSpel("#{body.payload.isVip ? 'V' : 'N'}", jsonPayload, String.class, "V");
            assertSpel("#{body.payload.items.size()}", jsonPayload, Integer.class, 2);
        }
    }

    @Nested
    @DisplayName("[Group D] XML Body node traversal and type casting")
    class XmlBodyTests {
        @Test void testXmlBody() {
            assertSpel("#{body.Header.GlobalId}", xmlPayload, String.class, "X999");
            assertSpel("#{T(Integer).parseInt(body.Body.Customer.Age) >= 30}", xmlPayload, Boolean.class, true);
            assertSpel("#{body.Body.Customer.Name.toUpperCase()}", xmlPayload, String.class, "TOBBY");
            assertSpel("#{body.Header.GlobalId + '-' + body.Body.Customer.Name}", xmlPayload, String.class, "X999-Tobby");
            assertSpel("#{body.Body.Customer.Age == '35' ? 'PASS' : 'FAIL'}", xmlPayload, String.class, "PASS");
        }
    }

    @Nested
    @DisplayName("[Group E] Fixed Body operations")
    class FixedBodyTests {
        @Test void testFixedBody() {
            assertSpel("#{body.BANK_CODE}", fixedPayload, String.class, "003");
            assertSpel("#{body.NAME}", fixedPayload, String.class, "Tobby");
            assertSpel("#{T(Integer).parseInt(body.AMT) * 2}", fixedPayload, Integer.class, 200000);
            assertSpel("#{body.REQ_TYPE == 'REQ'}", fixedPayload, Boolean.class, true);
            assertSpel("#{body.BANK_CODE.replace('003', 'IBK')}", fixedPayload, String.class, "IBK");
        }
    }

    @Nested
    @DisplayName("[Group F] Built-in class T() invocations")
    class JavaMethodTests {
        @Test void testJavaMethods() {
            assertSpel("#{T(java.util.UUID).randomUUID().toString().length()}", jsonPayload, Integer.class, 36);
            assertSpel("#{T(java.lang.Math).max(payload.headers.retryCount, 10)}", jsonPayload, Integer.class, 10);
            assertSpel("#{body.header.trxId.substring(1)}", jsonPayload, String.class, "123");
            assertSpel("#{body.header.trxId.contains('T')}", jsonPayload, Boolean.class, true);
            assertSpel("#{T(java.lang.System).currentTimeMillis() > 0}", jsonPayload, Boolean.class, true);
        }
    }

    @Nested
    @DisplayName("[Group G] Extreme Null defense (Applied Map Bracket notation)")
    class SafeNavigationTests {
        @Test void testNullDefenses() {
            // Access uncertain Map keys using bracket notation [''] to avoid MapAccessor exceptions
            assertSpel("#{body['missingNode'] != null ? body['missingNode']['value'] : null}", jsonPayload, String.class, null);
            assertSpel("#{body['missingNode'] != null ? body['missingNode']['value'] : 'DEF'}", jsonPayload, String.class, "DEF");
            assertSpel("#{body.payload.items.size() > 5 ? body.payload.items[5].id : null}", jsonPayload, Integer.class, null);
            assertSpel("#{payload.headers['missingVal'] == null}", jsonPayload, Boolean.class, true);
            assertSpel("#{payload.headers['missingVal'] != null ? payload.headers['missingVal'].length() : 0}", jsonPayload, Integer.class, 0);
        }
    }

    @Nested
    @DisplayName("[Group H] Collection/Map filtering (Selection & Projection)")
    class CollectionTests {
        @Test void testCollections() {
            assertSpel("#{payload.headers.strList.?[#this.startsWith('A')].size()}", jsonPayload, Integer.class, 2);
            assertSpel("#{payload.headers.strList.^[#this.contains('N')]}", jsonPayload, String.class, "BANANA");
            assertSpel("#{payload.headers.strList.![toLowerCase()].get(0)}", jsonPayload, String.class, "apple");
            assertSpel("#{body.header.containsKey('bankCode')}", jsonPayload, Boolean.class, true);
            assertSpel("#{T(java.lang.Boolean).valueOf('true')}", jsonPayload, Boolean.class, true);
        }
    }

    @Nested
    @DisplayName("[Group I] Regex-based data validation (Escape removed)")
    class RegexTests {
        @Test void testRegex() {
            assertSpel("#{body.header.trxId matches '^T[0-9]{3}$'}", jsonPayload, Boolean.class, true);
            assertSpel("#{body.BANK_CODE matches '^[0-9]+$'}", fixedPayload, Boolean.class, true);
            assertSpel("#{!(body.NAME matches '.*Admin.*')}", fixedPayload, Boolean.class, true);
            assertSpel("#{body.NAME.replaceAll('b{2}', 'B')}", fixedPayload, String.class, "ToBy");
        }
    }

    @Nested
    @DisplayName("[Group J] Legacy integration - Special character key access")
    class LegacyKeyTests {
        @Test void testLegacyKeys() {
            assertSpel("#{payload.headers['legacy.system.key']}", jsonPayload, String.class, "LEGACY_VAL");
            assertSpel("#{payload.headers['key with space']}", jsonPayload, String.class, "SPACE_VAL");
            assertSpel("#{payload.headers['biz' + 'Date']}", jsonPayload, String.class, "20260402");
        }
    }

    @Nested
    @DisplayName("[Group K] Advanced math operations and type casting limits (BigDecimal)")
    class MathAndCastingTests {
        @Test void testMath() {
            assertSpel("#{payload.headers.retryCount % 2 == 0}", jsonPayload, Boolean.class, true);
            assertSpel("#{payload.headers.retryCount ^ 3}", jsonPayload, Integer.class, 8);
            assertSpel("#{T(Integer).decode(payload.headers.hexVal) + 1}", jsonPayload, Integer.class, 256);
            assertSpel("#{new java.math.BigDecimal(payload.headers.threshold.toString()).multiply(new java.math.BigDecimal('2'))}", jsonPayload, java.math.BigDecimal.class, new java.math.BigDecimal("101.0"));
        }
    }

    @Nested
    @DisplayName("[Group L] Inline data structure creation and inclusion validation")
    class InlineStructureTests {
        @Test void testInlineStructures() {
            assertSpel("#{ {'003', '004', '088'}.contains(body.header.bankCode) }", jsonPayload, Boolean.class, true);
            assertSpel("#{ new int[]{100, 200, 300}[1] }", jsonPayload, Integer.class, 200);
            assertSpel("#{ {'A':'APPLE', 'B':'BANANA'}['B'] }", jsonPayload, String.class, "BANANA");
        }
    }

    @Nested
    @DisplayName("[Group M] Ultimate Collections - Chaining operations")
    class CollectionChainingTests {
        @Test void testCollectionChaining() {
            assertSpel("#{payload.headers.numList.?[#this > 30].size()}", jsonPayload, Integer.class, 2);
            assertSpel("#{payload.headers.numList.$[#this < 50]}", jsonPayload, Integer.class, 40);
            assertSpel("#{body.payload.items.![price].contains(250)}", jsonPayload, Boolean.class, true);
            assertSpel("#{body.payload.items.?[price > 100].![id].get(0)}", jsonPayload, Integer.class, 2);
            assertSpel("#{payload.headers.emptyList.isEmpty()}", jsonPayload, Boolean.class, true);
        }
    }

    @Nested
    @DisplayName("[Group N] Metadata (Telegram) based identity access")
    class ContextAndTypeTests {
        @Test void testContext() {
            // 1. Check MDC (identical)
            assertSpel("#{payload.mdcContext['traceId']}", jsonPayload, String.class, "MDC-9999");

            // 2. Access defined Telegram properties instead of internal class names
            assertSpel("#{telegram.name}", jsonPayload, String.class, "mock-json");
            assertSpel("#{telegram.type.name()}", jsonPayload, String.class, "JSON");

            // 3. Application: SpEL operating only for specific types
            assertSpel("#{telegram.type == T(io.zefio.core.payload.builder.config.Telegram.Type).JSON}",
                    jsonPayload, Boolean.class, true);
        }
    }

    @Nested
    @DisplayName("[Group O] Extreme conditional branching (Map Bracket handling)")
    class ConditionalTests {
        @Test void testConditionals() {
            assertSpel("#{body['notFound'] != null ? body['notFound'] : (payload.headers['missing'] != null ? payload.headers['missing'] : 'FINAL_FALLBACK')}", jsonPayload, String.class, "FINAL_FALLBACK");
            assertSpel("#{body.payload.items[1].price >= 200 ? 'HIGH' : (body.payload.items[1].price >= 100 ? 'MID' : 'LOW')}", jsonPayload, String.class, "HIGH");
        }
    }

    @Nested
    @DisplayName("[Group P] Breaking Reflection API limits")
    class ReflectionTests {
        @Test void testReflection() {
            assertSpel("#{payload.headers.retryCount instanceof T(Integer)}", jsonPayload, Boolean.class, true);
            assertSpel("#{new String(body.NAME).getBytes().length}", fixedPayload, Integer.class, 5);
            assertSpel("#{T(java.util.Base64).getEncoder().encodeToString(body.header.bankCode.getBytes())}", jsonPayload, String.class, java.util.Base64.getEncoder().encodeToString("003".getBytes()));
        }
    }

    @Nested
    @DisplayName("[Group Q] Exploiting XML structural blind spots")
    class XmlQuirkTests {
        @Test void testXmlQuirks() {
            assertSpel("#{body.Body.Customer.Name.contains('ob')}", xmlPayload, Boolean.class, true);
            assertSpel("#{body.Body.Customer.Age.length()}", xmlPayload, Integer.class, 2);
        }
    }

    @Nested
    @DisplayName("[Group R] Date & Time manipulation")
    class DateAndTimeTests {
        @Test void testDateAndTime() {
            assertSpel("#{payload.elapsedTime.time - payload.requestTime.time >= 0}", jsonPayload, Boolean.class, true);
            assertSpel("#{T(java.time.LocalDate).now().getYear() > 2020}", jsonPayload, Boolean.class, true);
        }
    }

    @Nested
    @DisplayName("[Group S] String splitting and array control (Index corrected)")
    class StringSplitTests {
        @Test void testStringSplit() {
            assertSpel("#{'A,B,C'.split(',')[1]}", jsonPayload, String.class, "B");
            assertSpel("#{body.REQ_TYPE + '-' + body.AMT.substring(3)}", fixedPayload, String.class, "REQ-100000");
        }
    }

    @Nested
    @DisplayName("[Group T] Madness - Cramming logic into one line")
    class MadnessTests {
        @Test void testMadness() {
            // Optimized to use SpEL's powerful inline projections (?.[], !.[]) instead of Java Stream pipelines
            String madExpression = "#{ body.payload.isVip and " +
                    "(body.payload.items.![price].?[#this > 150].size() > 0) " +
                    "? payload.headers['bizDate'] + '-' + body.header.bankCode " +
                    ": 'REJECT' }";
            assertSpel(madExpression, jsonPayload, String.class, "20260402-003");

            assertSpel("#{body['header'] != null && body['header']['fakeNode'] != null ? body['header']['fakeNode'].length() : -1}", jsonPayload, Integer.class, -1);
            assertSpel("#{payload.headers.strList.?[#this.length() == 5].get(0)}", jsonPayload, String.class, "APPLE");
            assertSpel("#{body.header.bankCode.equalsIgnoreCase('003')}", jsonPayload, Boolean.class, true);
            assertSpel("#{T(java.lang.Math).round(payload.headers.threshold)}", jsonPayload, Long.class, 51L);
            assertSpel("#{body.NAME.trim().isEmpty()}", fixedPayload, Boolean.class, false);
            assertSpel("#{payload.trxID + '|' + payload.currentEncoding.name()}", jsonPayload, String.class, "MOCK-TRX-ID|UTF-8");
        }
    }

    // =====================================================================
    // Additional 20 extreme scenarios extension (81 ~ 100)
    // =====================================================================

    @Nested
    @DisplayName("[Group U] XML Attribute control and JSON Null node defense")
    class DeepFormatTests {
        @Test void testFormatEdges() {
            assertSpel("#{body.Body.Customer._type}", xmlPayload, String.class, "VIP");
            assertSpel("#{body['payload']['status'] == null}", jsonPayload, Boolean.class, true);
            assertSpel("#{body['payload']['status'] != null ? body['payload']['status']['name'] : 'UNKNOWN'}", jsonPayload, String.class, "UNKNOWN");
            assertSpel("#{body.header.values().contains('003')}", jsonPayload, Boolean.class, true);
            assertSpel("#{body.DATE.substring(0,4) + 'Y'}", fixedPayload, String.class, "2026Y");
        }
    }

    @Nested
    @DisplayName("[Group V] Type casting formatting (String Formatting & Regex)")
    class FormatAndRegexTests {
        @Test void testFormatting() {
            assertSpel("#{T(java.lang.String).format('%08d', body.payload.items[0].price)}", jsonPayload, String.class, "00000100");
            assertSpel("#{body.BANK_CODE.getBytes()[0] > 0}", fixedPayload, Boolean.class, true);
            assertSpel("#{body.NAME.replaceAll('T','D').replaceAll('o','a')}", fixedPayload, String.class, "Dabby");
            assertSpel("#{T(java.lang.Integer).toHexString(body.payload.items[1].price)}", jsonPayload, String.class, "fa");
            assertSpel("#{body.header.trxId.toLowerCase().replace('t','X')}", jsonPayload, String.class, "X123");
        }
    }

    @Nested
    @DisplayName("[Group W] Defensive type validation and array utilities")
    class TypeGuardTests {
        @Test void testTypeGuards() {
            assertSpel("#{body.payload.isVip.class.name}", jsonPayload, String.class, "java.lang.Boolean");
            assertSpel("#{T(java.util.Arrays).stream(new int[]{5,2,9}).min().getAsInt()}", jsonPayload, Integer.class, 2);
            assertSpel("#{payload.headers.strList.subList(1,3).size()}", jsonPayload, Integer.class, 2);
            assertSpel("#{body.header.trxId != null ? body.header.trxId : 'NONE'}", jsonPayload, String.class, "T123");
            assertSpel("#{!!body.payload.isVip}", jsonPayload, Boolean.class, true);
        }
    }

    @Nested
    @DisplayName("[Group X] Final Boss - Highest difficulty business logic (Enhanced fixedEvent properties)")
    class FinalBossTests {
        @Test void testFinalBoss() {
            assertSpel("#{body.TAIL == 'TRX' ? 'TRANSACTION' : 'UNKNOWN'}", fixedPayload, String.class, "TRANSACTION");
            assertSpel("#{T(java.lang.String).join(',', body.payload.items.![id.toString()])}", jsonPayload, String.class, "1,2");
            assertSpel("#{T(java.time.LocalDate).parse(body.DATE, T(java.time.format.DateTimeFormatter).ofPattern('yyyyMMdd')).plusDays(1).toString()}", fixedPayload, String.class, "2026-04-03");
            assertSpel("#{T(java.lang.Math).random() >= 0.0}", jsonPayload, Boolean.class, true);

            // fixedEvent successfully mapped with strList property to pass operation
            String finalMadness = "#{ body.BANK_CODE + '-' + body.DATE.substring(0,4) + '-' + " +
                    "(payload.headers.strList.contains('APPLE') ? 'Y' : 'N') }";
            assertSpel(finalMadness, fixedPayload, String.class, "003-2026-Y");
        }
    }
}
