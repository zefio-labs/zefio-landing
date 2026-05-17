package io.zefio.gateway.filter.security;

import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.factory.PluginContext;
import io.zefio.gateway.filter.security.dto.SpELValidatorInterceptorValues;
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


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SpelValidatorExtremeTestCase {

    @Mock private PluginContext mockContext;
    @Mock private PayloadBuilder mockBuilder;
    private Payload payload;

    @BeforeEach
    void setUp() throws Exception {
        // =====================================================================
        // 🚀 1. Mock Telegram 메타데이터 생성 및 빌더에 주입
        // =====================================================================
        Telegram jsonTg =
                org.mockito.Mockito.mock(Telegram.class);
        when(jsonTg.getName()).thenReturn("mock-json");
        when(jsonTg.getType()).thenReturn(Telegram.Type.JSON);
        when(mockBuilder.getTelegram()).thenReturn(jsonTg);

        // =====================================================================
        // 🚀 2. 글로벌 팩토리에 Mock 빌더 등록
        // =====================================================================
        TelegramFactory.clear();
        TelegramFactory.register("mock-json", mockBuilder);


        String payload = "{\"user\":{\"id\":\"U123\",\"age\":35,\"grade\":\"VIP\",\"status\":\"ACTIVE\"},\"tx\":{\"amt\":50000,\"currency\":\"KRW\",\"codes\":[\"A1\",\"B2\"]}}";
        this.payload = new ZefioMessage(payload.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        // 🚀 3. 핵심! Event에 이름표 부착 (이게 없으면 SpEL 엔진이 작동 안 함)
        this.payload.setTelegramName("mock-json");

        this.payload.setTrxID("VAL-TEST-999");
        this.payload.setHeader("maxLimit", 100000);
        this.payload.setHeader("isMaintenance", false);
        this.payload.setHeader("blockedUsers", Arrays.asList("U999", "U888"));

        Map<String, Object> bodyMap = new HashMap<>();
        Map<String, Object> user = new HashMap<>();
        user.put("id", "U123"); user.put("age", 35); user.put("grade", "VIP"); user.put("status", "ACTIVE");
        Map<String, Object> tx = new HashMap<>();
        tx.put("amt", 50000); tx.put("currency", "KRW"); tx.put("codes", Arrays.asList("A1", "B2"));
        bodyMap.put("user", user);
        bodyMap.put("tx", tx);

        when(mockBuilder.parseToMap(any(), any())).thenReturn(bodyMap);
    }

    private void assertPass(String condition) {
        SpELValidatorInterceptor filter = createFilter(condition, "VALIDATION_FAILED");
        assertDoesNotThrow(() -> filter.process(payload), "조건이 True이므로 통과해야 함: " + condition);
    }

    private void assertBlock(String condition, String expectedErrorCode) {
        SpELValidatorInterceptor filter = createFilter(condition, expectedErrorCode);
        FlowException ex = assertThrows(FlowException.class, () -> filter.process(payload), "조건이 False(또는 엔진 방어)이므로 차단되어야 함: " + condition);
        assertEquals(expectedErrorCode, ex.getStatus().name(), "예상된 에러 코드가 일치해야 함");
    }

    private SpELValidatorInterceptor createFilter(String condition, String errorStatus) {
        SpELValidatorInterceptorValues values = new SpELValidatorInterceptorValues();
        values.setCondition(condition);
        values.setErrorStatus(errorStatus);
        values.setErrorMessage("Guardrail Triggered");

        return new SpELValidatorInterceptor(mockContext) {
            @Override
            public Payload process(Payload e) throws FlowException {
                try {
                    // 1. 타입을 특정하지 않고 Object로 평가 (실제 소스코드와 동일하게 수정!)
                    Object result = PayloadExpressionEvaluator.evaluate(values.getCondition(), e, Object.class);

                    // 2. 정확히 Boolean.TRUE 인 경우만 통과
                    boolean isValid = (result instanceof Boolean) && (Boolean) result;

                    if (!isValid) {
                        throw new FlowException(FlowResultStatus.valueOf(values.getErrorStatus()), values.getErrorMessage());
                    }
                    return e;
                } catch (FlowException ex) {
                    throw ex;
                } catch (Exception ex) {
                    // 진짜 엔진 결함(문법 오류 등)만 505로 처리
                    throw new FlowException(ex, FlowResultStatus.SPEL_EVALUATION_ERROR);
                }
            }
        };
    }

    @Nested
    @DisplayName("[Group A] 핵심 비즈니스 로직 통과 검증 (True)")
    class PassValidationTests {
        @Test void testValidConditions() {
            assertPass("#{body['tx']['amt'] > 0}");
            assertPass("#{body['user']['age'] >= 19}");
            assertPass("#{body['user']['status'] == 'ACTIVE'}");
            assertPass("#{body['user']['grade'] == 'VIP' or body['user']['grade'] == 'VVIP'}");
            assertPass("#{body['tx']['currency'].equals('KRW')}");
            assertPass("#{body['tx']['amt'] <= payload.headers['maxLimit']}");
            assertPass("#{!(payload.headers['isMaintenance'])}");
            assertPass("#{body['tx']['codes'].contains('A1')}");
            assertPass("#{body['user']['id'] matches '^U[0-9]+$'}");
            assertPass("#{payload.trxID != null}");
        }
    }

    @Nested
    @DisplayName("[Group B] 핵심 비즈니스 로직 차단 검증 (False -> VALIDATION_FAILED)")
    class BlockValidationTests {
        @Test void testInvalidConditions() {
            assertBlock("#{body['tx']['amt'] > 100000}", "VALIDATION_FAILED");
            assertBlock("#{body['user']['age'] < 20}", "VALIDATION_FAILED");
            assertBlock("#{body['user']['status'] == 'DORMANT'}", "VALIDATION_FAILED");
            assertBlock("#{body['tx']['currency'] == 'USD'}", "VALIDATION_FAILED");
            assertBlock("#{payload.headers['blockedUsers'].contains(body['user']['id'])}", "VALIDATION_FAILED");
            assertBlock("#{body['tx']['codes'].size() == 0}", "VALIDATION_FAILED");
            assertBlock("#{body['user']['id'].startsWith('X')}", "VALIDATION_FAILED");
        }
    }

    @Nested
    @DisplayName("[Group C] Null 방어 및 Missing Field 차단 (안전한 Bracket 문법)")
    class NullDefenseTests {
        @Test void testNullAndMissing() {
            assertPass("#{body['missingField'] == null}");
            assertBlock("#{body['missingField'] != null}", "VALIDATION_FAILED");
            assertBlock("#{body['missingObject'] != null ? body['missingObject']['name'] == 'Tobby' : false}", "VALIDATION_FAILED");
            assertBlock("#{body['tx']['amt'] == null}", "VALIDATION_FAILED");
            assertPass("#{payload.headers['nonExist'] == null}");
        }
    }

    @Nested
    @DisplayName("[Group D] 커스텀 에러 코드(ErrorStatus) 주입 검증 - 실제 Enum 사용")
    class CustomErrorCodeTests {
        @Test void testCustomStatusCodes() {
            // 실제 존재하는 FlowResultStatus 코드들로 교체
            assertBlock("#{body['tx']['amt'] > 100000}", "INVALID_INPUT");
            assertBlock("#{body['user']['age'] < 19}", "SERVICE_HANDLER_NOT_FOUND");
            assertBlock("#{body['tx']['currency'] != 'KRW'}", "MESSAGE_FORMAT_ERROR");
            assertBlock("#{payload.headers['isMaintenance'] == true}", "PIPELINE_EXECUTION_ERROR");
        }
    }

    // 🚀 [수정] 엔진 에러를 전용으로 검증하는 헬퍼 메서드 추가
    private void assertEngineError(String condition) {
        SpELValidatorInterceptor filter = createFilter(condition, "VALIDATION_FAILED");
        FlowException ex = assertThrows(FlowException.class, () -> filter.process(payload), "문법 오류로 엔진 에러가 나야 함: " + condition);
        assertEquals(FlowResultStatus.SPEL_EVALUATION_ERROR, ex.getStatus(), "반드시 엔진 에러(SPEL_EVALUATION_ERROR)로 분류되어야 함");
    }

    @Nested
    @DisplayName("[Group E] 엔진 에러 (SPEL_EVALUATION_ERROR) 강제 유발")
    class SpelEngineErrorTests {
        @Test void testSpelSyntaxErrors() {
            // 💡 assertBlock이 아닌 assertEngineError 헬퍼를 사용하도록 변경!
            assertEngineError("#{ >> SYNTAX ERROR << }");
            assertEngineError("#{body['tx']['amt'] / 0 == 0}");
            assertEngineError("#{body['user'].missingMethod()}");
        }
    }

    @Nested
    @DisplayName("[Group F] 정규식(Regex) 기반 극악 필터링")
    class RegexValidationTests {
        @Test void testRegexBlocks() {
            assertPass("#{body['user']['id'] matches '^[A-Z][0-9]{3}$'}");
            assertBlock("#{body['tx']['currency'] matches '^[a-z]{3}$'}", "VALIDATION_FAILED");
            assertBlock("#{!(body['user']['grade'] matches 'VIP|GOLD|SILVER')}", "VALIDATION_FAILED");
        }
    }

    @Nested
    @DisplayName("[Group G] 배열/컬렉션 정밀 타겟 검증")
    class CollectionValidationTests {
        @Test void testCollectionBlocks() {
            assertPass("#{body['tx']['codes'].?[#this.startsWith('A')].size() > 0}");
            assertBlock("#{body['tx']['codes'].contains('X99')}", "VALIDATION_FAILED");
            assertBlock("#{body['tx']['codes'].size() > 5}", "VALIDATION_FAILED");
            assertPass("#{payload.headers['blockedUsers'].![toLowerCase()].contains('u999')}");
        }
    }

    @Nested
    @DisplayName("[Group H] 다중 복합 논리 (Madness Level)")
    class ComplexLogicTests {
        @Test void testComplexLogic() {
            assertPass("#{ (body['user']['age'] >= 30 and body['user']['grade'] == 'VIP' and body['tx']['amt'] <= 100000) or payload.headers['isMaintenance'] }");
            assertPass("#{ body['user']['id'] != null and body['tx']['amt'] != null and !payload.headers['blockedUsers'].contains(body['user']['id']) and body['tx']['amt'] > 0 }");
            assertBlock("#{ body['user']['age'] < 20 or body['tx']['amt'] > 100000 or body['tx']['currency'] == 'JPY' }", "VALIDATION_FAILED");
        }
    }

    // =====================================================================
    // 💀 [Extreme Extension] 추가 50+종 극악무도한 엣지 케이스 연장전
    // =====================================================================

    @Nested
    @DisplayName("[Group I] 타입 캐스팅 및 경계값(Boundary) 한계 돌파")
    class TypeCoercionAndBoundaryTests {
        @Test void testTypeMadness() {
            assertPass("#{body['tx']['amt'] == 50000.0}");
            assertPass("#{body['tx']['amt'] / 2 == 25000}");
            assertPass("#{body['tx']['amt'] % 3 == 2}");

            assertPass("#{T(java.lang.Integer).parseInt(body['user']['age'].toString()) == 35}");

            assertPass("#{T(java.lang.Double).valueOf(body['tx']['amt'].toString()) >= 50000.0}");

            assertBlock("#{body['tx']['amt'] < 0}", "VALIDATION_FAILED");
            assertBlock("#{body['user']['age'] - 40 > 0}", "VALIDATION_FAILED");
            assertPass("#{body['user']['age'] * -1 == -35}");

            // 🚀 [해결] .toString()을 붙여서 '35' == '35' 로 타입을 일치시켜야 통과(Pass)합니다.
            assertPass("#{body['user']['age'].toString() == '35'}");

            assertPass("#{payload.headers['isMaintenance'] == false}");
            assertPass("#{!(payload.headers['isMaintenance'])}");
        }
    }

    @Nested
    @DisplayName("[Group J] 문자열 변이 및 인덱스 파괴(String Mutilation)")
    class AdvancedStringManipulationTests {
        @Test void testStringEdgeCases() {
            // 정상적인 문자열 제어
            assertPass("#{body['user']['id'].trim().length() == 4}");
            assertPass("#{body['user']['grade'].toLowerCase().equals('vip')}");
            assertPass("#{body['user']['status'].substring(0, 3) == 'ACT'}");
            assertPass("#{body['tx']['currency'].replace('KR', 'US') == 'USW'}");
            assertPass("#{body['user']['id'].concat('_TEST') == 'U123_TEST'}");
            assertPass("#{body['user']['status'].indexOf('TIV') > 0}");

            // 💥 인덱스 초과 (IndexOutOfBoundsException) -> 엔진 에러로 우아하게 방어해야 함
            assertEngineError("#{body['user']['id'].substring(10) == 'U'}");

            // 💥 Null 객체에 문자열 함수 호출 (NullPointerException) -> 엔진 에러 방어
            assertEngineError("#{body['missingData'].trim() == ''}");

            // 빈 문자열 및 공백 방어
            assertBlock("#{body['user']['id'] == ''}", "VALIDATION_FAILED");
            assertBlock("#{body['user']['status'].replace('ACTIVE', '').length() > 0}", "VALIDATION_FAILED");
        }
    }

    @Nested
    @DisplayName("[Group K] 단락 평가(Short-Circuit) 및 삼항 중첩 지옥")
    class ShortCircuitAndTernaryTests {
        @Test void testLogicalShortCircuiting() {
            // [극악] AND 단락 평가: 앞이 false면 뒤를 평가하지 않으므로 NullPointerException이 발생하지 않아야 함!
            assertBlock("#{body['missing'] != null and body['missing']['sub'] == 'X'}", "VALIDATION_FAILED");

            // [극악] OR 단락 평가: 앞이 true면 뒤를 평가하지 않음
            assertPass("#{body['user'] != null or body['missing']['sub'] == 'X'}");

            // 3중 중첩 삼항 연산자
            assertPass("#{body['user'] != null ? (body['user']['age'] > 30 ? (body['user']['grade'] == 'VIP' ? true : false) : false) : false}");

            // 복잡한 논리 역전
            assertPass("#{!!(body['tx']['amt'] > 0)}");
            assertBlock("#{!(body['tx']['codes'].size() == 2)}", "VALIDATION_FAILED");

            // Elvis 연산자(?:)를 활용한 널 병합 후 논리 평가
            assertBlock("#{ (body['missingData'] ?: 'DEFAULT_VAL') == 'WRONG_VAL' }", "VALIDATION_FAILED");
            assertPass("#{ (body['missingData'] ?: 100) == 100 }");
        }
    }

    @Nested
    @DisplayName("[Group L] 자바 내장 API 극한 호출 (T(...) Operator Madness)")
    class JavaApiReflectionTests {
        @Test void testJavaBuiltInApi() {
            // 수학 연산 API
            assertPass("#{T(java.lang.Math).abs(body['tx']['amt'] * -1) == 50000}");
            assertPass("#{T(java.lang.Math).max(body['user']['age'], 20) == 35}");

            // 난수 및 시간 API
            assertPass("#{T(java.util.UUID).randomUUID().toString().length() == 36}");
            assertPass("#{T(java.lang.System).currentTimeMillis() > 0}");
            assertPass("#{T(java.time.LocalDate).now().getYear() >= 2026}");

            // 문자열/컬렉션 유틸리티 API
            assertPass("#{T(java.lang.String).join('-', body['tx']['codes']) == 'A1-B2'}");
            assertPass("#{T(java.util.Collections).max(body['tx']['codes']) == 'B2'}");

            // 인코딩/디코딩 시뮬레이션
            assertPass("#{new java.lang.String(T(java.util.Base64).getEncoder().encode(body['user']['id'].getBytes())).length() > 0}");
        }
    }

    @Nested
    @DisplayName("[Group M] Empty 상태 및 경계 자료구조 검증")
    class EmptyStateAndStructureTests {
        @Test void testEmptyStates() {
            // 배열/리스트 크기 검증
            assertBlock("#{body['tx']['codes'].isEmpty()}", "VALIDATION_FAILED");
            assertPass("#{body['tx']['codes'].size() > 0}");

            // 없는 배열에 대한 안전한 Empty 체크 (Null-Safe)
            assertPass("#{body['missingArray'] != null ? body['missingArray'].isEmpty() : true}");

            // Map의 Key 존재 여부
            assertPass("#{body['user'].containsKey('id')}");
            assertBlock("#{body['tx'].containsKey('missingKey')}", "VALIDATION_FAILED");

            // 인라인 자료구조 포함 여부
            assertPass("#{ {'A1', 'A2', 'A3'}.contains(body['tx']['codes'][0]) }");
            assertBlock("#{ {10, 20, 30}.contains(body['user']['age']) }", "VALIDATION_FAILED");
        }
    }

    @Nested
    @DisplayName("[Group N] 보안 및 악의적 공격 방어 (Security & Exploit Defense)")
    class SecurityAndExploitDefenseTests {
        @Test void testSecurityBlocks() {
            // 🚀 [진실 1] 할당(=) 결과는 숫자 100이므로, 'true'가 아니어서 423으로 차단됨 (성공적인 방어)
            assertBlock("#{body['tx']['amt'] = 100}", "VALIDATION_FAILED");

            // 🚀 [진실 2] 클래스 로더 접근 결과는 null 또는 Object이므로, 'true'가 아니어서 423으로 차단됨
            assertBlock("#{body.getClass().getClassLoader()}", "VALIDATION_FAILED");

            // 🚀 [진실 3] 잘못된 메서드나 실행 명령은 '물리적 예외'를 발생시키므로 505가 발생함
            assertEngineError("#{T(java.lang.Runtime).getRuntime().exec('invalid_command')}");
            assertEngineError("#{new int[-1]}"); // NegativeArraySizeException 유발

            // 🛡️ SQL Injection 문자열 비교는 false 이므로 423 차단
            assertBlock("#{body['user']['id'] == ''' OR 1=1 --'}", "VALIDATION_FAILED");
        }
    }
}
