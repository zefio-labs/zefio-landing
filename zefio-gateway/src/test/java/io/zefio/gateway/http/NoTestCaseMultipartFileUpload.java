package io.zefio.gateway.http;

import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class NoTestCaseMultipartFileUpload {

    public static void main(String[] args) throws IOException {
        // 업로드할 파일들
        Map<String, Path> files = new HashMap<>();
        Path basePath = Paths.get(System.getProperty("user.dir"), "link-http", "src", "test", "resources", "upload");

        files.put("file1", basePath.resolve("multipart_1byte.log"));
        files.put("file2", basePath.resolve("multipart_2mb.log"));
        files.put("file3", basePath.resolve("sscard-20250919.zip"));

        // 파일 존재 여부 확인
        for (Path p : files.values()) {
            assertTrue(p.toFile().exists(), "File not found: " + p);
        }

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(10000)              // 연결 시도 제한 (10초)
                .setConnectionRequestTimeout(5000)     // 커넥션 풀에서 대기 제한
                .setSocketTimeout(300000)               // 데이터 응답 대기 제한 5분
                .build();

        // HttpClient 생성
        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {
            HttpPost uploadPost = new HttpPost("http://localhost:51009/");
//            HttpPost uploadPost = new HttpPost("http://192.168.1.225:51009/");


            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);

            // 각 파일 스트리밍 추가
            for (Map.Entry<String, Path> entry : files.entrySet()) {
                builder.addBinaryBody(
                        entry.getKey(),
                        entry.getValue().toFile(),
                        ContentType.DEFAULT_BINARY,
                        entry.getValue().getFileName().toString()
                );
            }

            HttpEntity multipart = builder.build();
            uploadPost.setEntity(multipart);

            // 요청 실행
            try (CloseableHttpResponse response = httpClient.execute(uploadPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                assertTrue(statusCode >= 200 && statusCode < 300, "Invalid response code: " + statusCode);

                String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
                assertNotNull(responseBody);
                assertFalse(responseBody.isEmpty());

                System.out.println("Multi file upload response code: " + statusCode);
                System.out.println("Response body:\n" + responseBody);
            }
        }
    }
}
