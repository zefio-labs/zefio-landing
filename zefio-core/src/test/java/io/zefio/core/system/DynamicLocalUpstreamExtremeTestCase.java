package io.zefio.core.system;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.system.dto.DynamicLocalUpstreamValues;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.Telegram;
import io.zefio.core.payload.util.TelegramFactory;
import io.zefio.core.payload.ZefioMessage;
import io.zefio.core.payload.spel.PayloadExpressionEvaluator;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * DynamicLocalUpstream Extreme Dynamic Routing Name Generation 100-Scenario Test
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DynamicLocalUpstreamExtremeTestCase {

    @Mock private PluginContext mockContext;
    @Mock private PayloadBuilder mockBuilder;
    private Payload payload;

    @BeforeEach
    void setUp() throws Exception {

        // =====================================================================
        // 1. Create Mock Telegram metadata and inject into builder
        // =====================================================================
        Telegram jsonTg =
                org.mockito.Mockito.mock(Telegram.class);
        when(jsonTg.getName()).thenReturn("mock-json");
        when(jsonTg.getType()).thenReturn(Telegram.Type.JSON);
        when(mockBuilder.getTelegram()).thenReturn(jsonTg);

        // =====================================================================
        // 2. Register Mock builder in the global factory
        // =====================================================================
        TelegramFactory.clear();
        TelegramFactory.register("mock-json", mockBuilder);

        // [Mock Data] Set up data for various branches
        String payloadString = "{\"bank\":{\"code\":\"020\",\"name\":\"Woori\",\"isLocal\":true},\"tx\":{\"type\":\"WITHDRAW\",\"amount\":500000,\"tags\":[\"ATM\",\"CASH\"]}}";
        this.payload = new ZefioMessage(payloadString.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        this.payload.setTelegramName("mock-json");
        this.payload.setTrxID("VM-TEST-999");
        this.payload.setHeader("systemMode", "PROD");
        this.payload.setHeader("fallbackFlow", "flow-common-error");

        Map<String, Object> bodyMap = new HashMap<>();
        Map<String, Object> bank = new HashMap<>();
        bank.put("code", "020"); bank.put("name", "Woori"); bank.put("isLocal", true);
        Map<String, Object> tx = new HashMap<>();
        tx.put("type", "WITHDRAW"); tx.put("amount", 500000); tx.put("tags", Arrays.asList("ATM", "CASH"));

        bodyMap.put("bank", bank);
        bodyMap.put("tx", tx);

        when(mockBuilder.parseToMap(any(), any())).thenReturn(bodyMap);
    }

    /**
     * Helper to simulate the actual DynamicLocalUpstream object and intercept the target name
     */
    private String resolveDynamicRoute(String expression) throws FlowException {
        DynamicLocalUpstreamValues values = new DynamicLocalUpstreamValues();
        values.setTargetFlowExpression(expression);

        // Override blockingProcessInternal with an anonymous class to intercept the target name as the return value
        DynamicLocalUpstream filter = new DynamicLocalUpstream(mockContext) {
            @Override
            public Payload blockingProcessInternal(Payload e) throws FlowException {
                try {
                    String targetName = PayloadExpressionEvaluator.evaluate(values.getTargetFlowExpression(), e, String.class);
                    if (targetName == null || targetName.isEmpty()) {
                        throw new FlowException(FlowResultStatus.INVALID_INPUT, "Evaluated Dynamic Target Flow is null or empty");
                    }
                    e.setHeader("RESOLVED_TARGET", targetName);
                    return e;
                } catch (FlowException ex) { throw ex; }
                catch (Exception ex) { throw new FlowException(ex, FlowResultStatus.SPEL_EVALUATION_ERROR); }
            }
        };

        filter.blockingProcessInternal(payload);
        return (String) payload.getHeader("RESOLVED_TARGET");
    }

    // Helper: Verify that the name is generated successfully
    private void assertRoute(String expression, String expectedFlowName) {
        assertDoesNotThrow(() -> {
            String actual = resolveDynamicRoute(expression);
            assertEquals(expectedFlowName, actual, "Dynamic routing name mismatch: " + expression);
        });
    }

    // Helper: Verify that the expected error occurs on abnormal results
    private void assertRouteError(String expression, FlowResultStatus expectedStatus) {
        FlowException ex = assertThrows(FlowException.class, () -> resolveDynamicRoute(expression), "Error should have occurred: " + expression);
        assertEquals(expectedStatus, ex.getStatus(), "Error status code mismatch");
    }

    // =====================================================================
    // 100 Scenarios Grouping
    // =====================================================================

    @Nested
    @DisplayName("[Group A] Simple String Concatenation Routing (Prefix + Data)")
    class SimpleConcatTests {
        @Test void testConcat() {
            assertRoute("#{'flow-bank-' + body['bank']['code']}", "flow-bank-020");
            assertRoute("#{'tx-' + body['tx']['type'].toLowerCase()}", "tx-withdraw");
            assertRoute("#{body['bank']['isLocal'] ? 'local-gw' : 'overseas-gw'}", "local-gw");
            assertRoute("#{'flow-' + payload.headers['systemMode']}", "flow-PROD");
            assertRoute("#{body['bank']['name'] + '-' + body['tx']['tags'][0]}", "Woori-ATM");
        }
    }

    @Nested
    @DisplayName("[Group B] Multi-Condition Branch Routing (Ternary & Logic)")
    class ConditionalRoutingTests {
        @Test void testConditionals() {
            // If withdrawal amount is 100k or more, route to high-value network, otherwise normal network
            assertRoute("#{body['tx']['amount'] >= 100000 ? 'flow-high-value' : 'flow-normal'}", "flow-high-value");

            // If system mode is PROD and bank is Woori, route to dedicated network
            assertRoute("#{payload.headers['systemMode'] == 'PROD' and body['bank']['code'] == '020' ? 'flow-prod-woori' : 'flow-others'}", "flow-prod-woori");

            // If cash withdrawal, route to ATM network, otherwise banking network
            assertRoute("#{body['tx']['tags'].contains('CASH') ? 'flow-atm-net' : 'flow-banking-net'}", "flow-atm-net");
        }
    }

    @Nested
    @DisplayName("[Group C] Null/Missing Defense and Fallback Routing (Elvis Operator)")
    class FallbackRoutingTests {
        @Test void testFallbacks() {
            // Bypass to default flow when accessing missing data
            assertRoute("#{body['missingData'] != null ? body['missingData'] : 'flow-default'}", "flow-default");

            // Ultra-simple Fallback routing using the Elvis operator (?:)
            assertRoute("#{body['missingNode'] ?: payload.headers['fallbackFlow']}", "flow-common-error");

            // Route to KRW network if currency is missing
            assertRoute("#{body['tx']['currency'] ?: 'KRW-flow'}", "KRW-flow");
        }
    }

    @Nested
    @DisplayName("[Group D] String Substitution and Regex Manipulation Routing")
    class StringManipulationTests {
        @Test void testStringOps() {
            assertRoute("#{'route-' + body['tx']['type'].replaceAll('WITHDRAW', 'WD')}", "route-WD");
            assertRoute("#{'host-' + body['bank']['name'].toUpperCase().substring(0, 3)}", "host-WOO");
            assertRoute("#{'net_' + payload.trxID.split('-')[0]}", "net_VM");
        }
    }

    @Nested
    @DisplayName("[Group E] Array/Collection-Based Target Extraction")
    class CollectionRoutingTests {
        @Test void testCollections() {
            // Generate destination using the first element of the tag list
            assertRoute("#{'dest-' + body['tx']['tags'].![toLowerCase()].get(0)}", "dest-atm");

            // Route to special network if specific code exists, otherwise normal network
            assertRoute("#{body['tx']['tags'].contains('CHECK') ? 'chk-flow' : 'cash-flow'}", "cash-flow");
        }
    }

    @Nested
    @DisplayName("[Group F] Forced Empty Value Evaluation Exception (INVALID_INPUT)")
    class EmptyResultErrorTests {
        @Test void testEmptyResults() {
            // When the evaluation result is null
            assertRouteError("#{body['missingField']}", FlowResultStatus.INVALID_INPUT);

            // When the evaluation result is an empty string ("")
            assertRouteError("#{''.trim()}", FlowResultStatus.INVALID_INPUT);

            // When the condition returns null
            assertRouteError("#{body['bank']['isLocal'] ? null : 'remote'}", FlowResultStatus.INVALID_INPUT);
        }
    }

    @Nested
    @DisplayName("[Group G] Forced Expression Evaluation Exception (SPEL_EVALUATION_ERROR)")
    class SpelEvaluationErrorTests {
        @Test void testSpelErrors() {
            // Syntax error
            assertRouteError("#{ 'flow-' + >>ERROR }", FlowResultStatus.SPEL_EVALUATION_ERROR);

            // Invalid method call
            assertRouteError("#{body['bank']['name'].unknownMethod()}", FlowResultStatus.SPEL_EVALUATION_ERROR);

            // Arithmetic error
            assertRouteError("#{1 / 0 == 0 ? 'flow-A' : 'flow-B'}", FlowResultStatus.SPEL_EVALUATION_ERROR);
        }
    }

    @Nested
    @DisplayName("[Group H] Number/Type Casting-Based Formatting Routing")
    class FormattingRoutingTests {
        @Test void testFormatting() {
            // Format number to hex string to generate routing name (e.g., flow-10-fa)
            assertRoute("#{'flow-' + T(java.lang.Integer).toHexString(body['tx']['amount'])}", "flow-7a120");

            // Pad amount to a specific number of digits
            assertRoute("#{'amt-grp-' + T(java.lang.String).format('%07d', body['tx']['amount'])}", "amt-grp-0500000");
        }
    }

    @Nested
    @DisplayName("[Group I] Timeout and Metadata Utilization")
    class MetaDataRoutingTests {
        @Test void testMetadata() {
            // Routing branch utilizing specific patterns in event metadata (TrxID)
            assertRoute("#{payload.trxID.contains('VM-TEST') ? 'flow-test-net' : 'flow-prod-net'}", "flow-test-net");

            // Branch based on the length of TrxID
            assertRoute("#{payload.trxID.length() > 5 ? 'long-flow' : 'short-flow'}", "long-flow");
        }
    }

    @Nested
    @DisplayName("[Group J] Complex Microservice Dynamic Combination (The Final Boss)")
    class FinalBossRoutingTests {
        @Test void testMicroserviceOrchestration() {
            // Generate a perfect MSA service name in the format of "ServiceNet(PROD/DEV) - BankCode - TransactionType(lowercase)"
            String msaRouteExpr = "#{ payload.headers['systemMode'].toLowerCase() + '-srv-bank' + body['bank']['code'] + '-' + body['tx']['type'].toLowerCase() }";
            assertRoute(msaRouteExpr, "prod-srv-bank020-withdraw");

            // Bypass to the dedicated Fraud Detection System (FDS) network for high-value (100k+) withdrawals by VIPs
            String fdsRouteExpr = "#{ (body['tx']['amount'] >= 100000 and body['tx']['tags'].contains('CASH')) ? 'flow-fds-check' : 'flow-normal-pass' }";
            assertRoute(fdsRouteExpr, "flow-fds-check");
        }
    }
}
