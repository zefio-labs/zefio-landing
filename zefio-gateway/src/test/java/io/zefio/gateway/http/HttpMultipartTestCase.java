package io.zefio.gateway.http;

import io.zefio.core.Ingress;
import io.zefio.core.Upstream;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.ExchangePattern;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.core.payload.builder.config.*;
import io.zefio.testsupport.endpoint.UpstreamToIngressIntegrationTestCase;
import io.zefio.testsupport.payload.FixedPayloadBuilderFactory;
import io.zefio.testsupport.payload.IPayloadBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Http Multipart 업로드 테스트")
public class HttpMultipartTestCase extends UpstreamToIngressIntegrationTestCase {

    public HttpMultipartTestCase() throws Exception {
        super("httpInbound_multipart", "httpOutbound_multipart");
    }

    @Override
    public IPayloadBuilderFactory createSenderBuilder() throws Exception {
        // 1. Framing 전략 설정 (v2.4.2 규격: MOM 환경에 맞는 프레이밍)
        // JMS는 메시지 자체가 하나의 단위이므로 EOF(분할 없음) 타입을 주로 사용합니다.
        FramingField framing = new FramingField();
        framing.setType(FramingType.EOF);

        // 🚀 [v2.6 은탄환] HTTP Multipart는 바디 파싱이 불가능하므로,
        // SpEL의 '정적 문자열(Static String)' 기능을 활용하여 고정된 TrxID를 부여합니다.
        // 💡 표현식 주의: 따옴표 안에 홑따옴표가 들어간 "#{'문자열'}" 형태입니다.
        CorrelationField correlation = new CorrelationField(CorrelationIdType.SpEL);
        correlation.setExpression("#{'MULTIPART-TEST-TRX-ID'}");

        // 3. Telegram 및 빌더 생성 (JsonValues @Builder 활용)
        FixedValues.FixedField rawField = new FixedValues.FixedField();
        rawField.setName("RAW_BODY");
        rawField.setLength(0); // 0은 가변/나머지 전체를 의미하는 규약
        rawField.setTrim(false); // Multipart 데이터는 공백 제거(trim)를 하면 안 되므로 명시적 설정 권장

        PayloadBuilder builder = new Telegram.Builder()
                .name("http-multipart-raw")
                .type(Telegram.Type.Fixed)
                .values(FixedValues.builder()
                        .framing(framing)
                        .correlation(correlation)
                        // 🚀 레이아웃을 추가하려면 이렇게 전체를 하나의 필드로 잡으면 됩니다.
                        .layout(Collections.singletonList(rawField))
                        .encodingIgnore(true)
                        .build())
                .build();

        return new FixedPayloadBuilderFactory(builder, senderEncoding);
    }

    @Override
    public Ingress createReceiver(PluginContext.PluginContextBuilder builder) {
        PluginContext context = builder
                .exchangePattern(ExchangePattern.RequestReply)
                .build();
        return new HttpIngress(context);
    }

    @Override
    public Upstream createSender(PluginContext.PluginContextBuilder builder) {
        PluginContext context = builder
                .exchangePattern(ExchangePattern.RequestReply)
                .build();
        return new HttpUpstream(context);
    }

    @Override
    public Payload handleFilter(Payload requestPayload) throws Exception {
        // 서버 측에서 업로드된 파일 이름을 응답으로 돌려주는 형태 시뮬레이션
        String responseMessage = "{\"result\":\"OK\",\"filename\":\"upload_test.txt\"}";
        requestPayload.setBody(responseMessage.getBytes(requestPayload.getCurrentEncoding()));

        return requestPayload;
    }

    @Test
    @DisplayName("Http Multipart 파일 업로드 송수신 테스트")
    void testHttpMultipartUpload() throws Exception {
        // 업로드할 테스트 파일 생성
//        Paths.get("src", "test", "resources", dirName).toAbsolutePath()
        File file = new File("src/test/resources/upload/multipart_1byte.log");
        assertTrue(file.exists(), "테스트 파일이 존재해야 합니다.");

        // 파일을 byte[] 로 읽기
        byte[] fileBytes = readFileToBytes(file);

        // multipart 형식으로 body 구성 (간단히 boundary 포함)
        String boundary = "----BoundaryTest1234";
        String multipartBody =
                "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n" +
                        "Content-Type: text/plain\r\n\r\n" +
                        new String(fileBytes, senderEncoding) + "\r\n" +
                        "--" + boundary + "--\r\n";

        Payload requestPayload = senderBuilder.withBody(multipartBody.getBytes(senderEncoding), senderEncoding);

        // 실제 송수신 수행
        sender.executeAsync(requestPayload, Executors.newSingleThreadExecutor()).join();

        // Inbound 가 받은 이벤트
        Payload responsePayload = getReceiverCapturedEvent();

        assertNotNull(responsePayload);
        assertNotNull(responsePayload.getBody());

        String actualResponse = new String(responsePayload.getBody(), responsePayload.getCurrentEncoding());
        System.out.println("Response Body = " + actualResponse);

        assertTrue(actualResponse.contains("OK"));
    }

    private static byte[] readFileToBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192]; // 8KB 버퍼
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toByteArray();
        }
    }
}
