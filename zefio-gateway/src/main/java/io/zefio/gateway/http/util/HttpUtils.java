package io.zefio.gateway.http.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.common.util.CustomMultipartFile;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.PayloadHeaders;
import io.zefio.core.payload.spel.PayloadExpressionEvaluator;
import io.zefio.gateway.http.dto.HttpUpstreamValues;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core utility class for HTTP protocol operations.
 * Handles Status Code mapping, URI resolution, Charset extraction, and Multipart file processing.
 */
public class HttpUtils {
    private static final Logger log = LoggerFactory.getLogger(HttpUtils.class);

    /**
     * Maps an internal FlowResultStatus to a standard HTTP Response Status Code.
     */
    public static HttpResponseStatus toHttpResponseStatus(FlowResultStatus status) {
        switch (status) {
            // [4xx Client Errors]
            case BAD_REQUEST:
            case NOT_CORRELATION_KEY:
                return HttpResponseStatus.BAD_REQUEST;          // 400

            case UNAUTHORIZED:
                return HttpResponseStatus.UNAUTHORIZED;         // 401

            case FORBIDDEN:
                return HttpResponseStatus.FORBIDDEN;            // 403

            case NOT_FOUND:
            case GONE:
            case REMOTE_NOT_FOUND:           // External target path missing
            case SERVICE_HANDLER_NOT_FOUND:  // Internal handler missing
                return HttpResponseStatus.NOT_FOUND;            // 404

            case METHOD_NOT_ALLOWED:
                return HttpResponseStatus.METHOD_NOT_ALLOWED;   // 405

            case DUPLICATE_REQUEST:
            case ALREADY_COMPLETED:          // Collision on duplicate transaction IDs
                return HttpResponseStatus.CONFLICT;             // 409

            case INVALID_INPUT:
                return HttpResponseStatus.UNPROCESSABLE_ENTITY; // 422

            // [5xx Server Errors]
            case REMOTE_SERVER_ERROR:        // Target server returned 5xx
            case REMOTE_CLIENT_ERROR:        // Target server returned 4xx (acts as gateway error here)
            case NETWORK_ERROR:              // Target connection failure
            case ALREADY_CLOSED:             // Socket disconnected unexpectedly
            case NOT_COMPLETE_CLOSE:
                return HttpResponseStatus.BAD_GATEWAY;          // 502

            case SYSTEM_BUSY:
            case CONNECTION_POOL_EXHAUSTED:   // External connection pool empty
            case QUEUE_CAPACITY_EXCEEDED:     // Internal task queue full
            case SYSTEM_SHUTDOWN:            // Engine gracefully shutting down
            case DATABASE_TIMEOUT:           // DB delays
            case ASYNC_EXECUTION_ERROR:      // Worker pool issues
                return HttpResponseStatus.SERVICE_UNAVAILABLE;  // 503

            case TIMEOUT:                    // Target response delay
                return HttpResponseStatus.GATEWAY_TIMEOUT;      // 504

            case INTERNAL_SERVER_ERROR:
            case UNKNOWN:
            case INTERRUPTED:
            default:
                return HttpResponseStatus.INTERNAL_SERVER_ERROR;// 500
        }
    }

    /**
     * Maps an external HTTP Status Code back to an internal FlowResultStatus.
     */
    public static FlowResultStatus fromHttpStatusCode(int statusCode) {
        if (statusCode == 503) return FlowResultStatus.REMOTE_SERVER_ERROR;
        if (statusCode == 504) return FlowResultStatus.TIMEOUT;
        if (statusCode == 502) return FlowResultStatus.NETWORK_ERROR;

        if (statusCode >= 500) {
            return FlowResultStatus.REMOTE_SERVER_ERROR;
        }

        if (statusCode == 400) return FlowResultStatus.BAD_REQUEST;
        if (statusCode == 401) return FlowResultStatus.UNAUTHORIZED;
        if (statusCode == 403) return FlowResultStatus.FORBIDDEN;
        if (statusCode == 404) return FlowResultStatus.REMOTE_NOT_FOUND;
        if (statusCode == 405) return FlowResultStatus.METHOD_NOT_ALLOWED;
        if (statusCode == 409) return FlowResultStatus.DUPLICATE_REQUEST;
        if (statusCode == 410) return FlowResultStatus.GONE;
        if (statusCode == 422) return FlowResultStatus.INVALID_INPUT;

        if (statusCode >= 400) {
            return FlowResultStatus.REMOTE_CLIENT_ERROR;
        }

        return FlowResultStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * Evaluates dynamic header expressions using SpEL.
     */
    public static List<Pair<String, String>> getHttpHeaderKeyValueConvertor(Map<String, String> requestHeaderKeyValues, Payload payload, Charset requestEncoding) throws FlowException {
        List<Pair<String, String>> pairList = new ArrayList<>();

        for (Map.Entry<String, String> entry : requestHeaderKeyValues.entrySet()) {
            String key = entry.getKey();
            String rawValue = entry.getValue();

            // Execute SpEL expression to dynamically inject payload data into headers
            String resolvedValue = PayloadExpressionEvaluator.evaluate(rawValue, payload, String.class);
            pairList.add(Pair.with(key, resolvedValue));
        }
        return pairList;
    }

    /**
     * Resolves the target URL for an Upstream HTTP request.
     */
    public static String resolveUrl(HttpUpstreamValues values, Payload payload, boolean useHttps) {
        String pathExpr = values.getRequestPath();

        String resolvedPath;
        if (pathExpr.startsWith("#{") || pathExpr.contains("#{")) {
            // Modern strategy: Evaluate full path via SpEL
            resolvedPath = PayloadExpressionEvaluator.evaluate(pathExpr, payload, String.class);
        } else {
            // Legacy strategy: Extract dynamic tags based on body offsets
            resolvedPath = legacyResolvePath(pathExpr, payload, values.getRequestEncoding());
        }

        String scheme = useHttps ? "https" : "http";
        return String.format("%s://%s:%d/%s", scheme, values.getHost(), values.getUpstream().getPort(),
                resolvedPath.startsWith("/") ? resolvedPath.substring(1) : resolvedPath);
    }

    /**
     * Legacy offset-based path resolution for backward compatibility.
     * Expects format: "startOffset-endOffset, /base/path/"
     */
    private static String legacyResolvePath(String requestPath, Payload payload, Charset encoding) {
        if (requestPath == null || requestPath.isEmpty()) {
            return "/";
        }

        String offsetPart = null;
        String basePath = null;

        if (requestPath.contains(",")) {
            String[] parts = requestPath.split(",", 2);
            offsetPart = parts[0].trim();
            basePath = parts[1].trim();
        } else {
            basePath = requestPath.trim();
        }

        if (offsetPart == null) {
            return basePath;
        }

        Pattern pattern = Pattern.compile("^(\\d+)-(\\d+)$");
        Matcher matcher = pattern.matcher(offsetPart);

        if (!matcher.matches()) {
            log.error("Invalid legacy offset format: [{}]. Expected 'start-end'.", offsetPart);
            throw new IllegalArgumentException("Invalid offset format: " + offsetPart);
        }

        try {
            int start = Integer.parseInt(matcher.group(1));
            int end = Integer.parseInt(matcher.group(2));

            byte[] body = payload.getBody();
            if (body == null || body.length < end) {
                throw new IllegalArgumentException("Body length is shorter than requested offset end index.");
            }

            String tag = new String(body, start, end - start, encoding).trim();
            String encodedTag = URLEncoder.encode(tag, encoding.name());

            return basePath + encodedTag;

        } catch (Exception e) {
            log.error("Failed to resolve legacy path from offset: {}", e.getMessage());
            return basePath;
        }
    }

    /**
     * Safely extracts the character set from the Content-Type header.
     */
    public static Charset resolveCharsetFromContentType(HttpHeaders headers, Charset fallbackEncoding) {
        if (headers == null) return fallbackEncoding;

        String contentTypeHeader = headers.get(HttpHeaderNames.CONTENT_TYPE);
        if (contentTypeHeader != null) {
            String[] parts = contentTypeHeader.split(";");
            for (String part : parts) {
                part = part.trim().toLowerCase();
                if (part.startsWith("charset=")) {
                    String charsetName = part.substring("charset=".length()).trim();
                    try {
                        return Charset.forName(charsetName);
                    } catch (Exception e) {
                        log.warn("Unsupported charset [{}], fallback to default: {}", charsetName, fallbackEncoding);
                        break;
                    }
                }
            }
        }
        return fallbackEncoding;
    }

    // ==========================================================
    // Multipart Operations
    // ==========================================================

    public static final String LINE_FEED = "\r\n";

    public static String generateBoundary() {
        return "----Boundary" + System.currentTimeMillis();
    }

    public static byte[] readByteBufToBytes(ByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    /**
     * Writes a Spring MultipartFile directly into a Netty ByteBuf for transmission.
     */
    public static void addMultipartFile(ByteBuf buffer, String boundary, MultipartFile file) {
        addBoundary(buffer, boundary);
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        buffer.writeBytes(("Content-Disposition: form-data; name=\"" + file.getName() + "\"; filename=\"" + filename + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        buffer.writeBytes(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        try {
            buffer.writeBytes(Unpooled.wrappedBuffer(file.getBytes()));
        } catch (Exception e) {
            throw new FlowException(e, FlowResultStatus.MESSAGE_FORMAT_ERROR);
        }
        buffer.writeBytes(LINE_FEED.getBytes(StandardCharsets.UTF_8));
    }

    public static void addBoundary(ByteBuf buffer, String boundary) {
        buffer.writeBytes(("--" + boundary + LINE_FEED).getBytes(StandardCharsets.UTF_8));
    }

    public static void addMultipartEndBoundary(ByteBuf buffer, String boundary) {
        buffer.writeBytes(("--" + boundary + "--" + LINE_FEED).getBytes(StandardCharsets.UTF_8));
    }

    public static String extractBoundary(String contentTypeHeader) {
        if (contentTypeHeader == null) return null;
        Matcher matcher = Pattern.compile("boundary=([^;]+)").matcher(contentTypeHeader);
        if (matcher.find()) {
            return matcher.group(1).replace("\"", "");
        }
        return null;
    }

    /**
     * Parses a raw ByteBuf containing a multipart response and promotes files into the Payload headers.
     */
    public static void parseMultipartResponse(ByteBuf content, String boundary, Payload payload) throws Exception {
        // Create a synthetic HttpRequest to leverage Netty's HttpPostRequestDecoder
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.set(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);

        FullHttpRequest fakeRequest = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                "/dummy",
                content
        );
        fakeRequest.headers().set(headers);

        HttpDataFactory factory = new DefaultHttpDataFactory(true);
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(factory, fakeRequest, StandardCharsets.UTF_8);

        List<MultipartFile> responseFiles = new ArrayList<>();
        try {
            while (decoder.hasNext()) {
                InterfaceHttpData data = decoder.next();
                if (data != null && data.getHttpDataType() == InterfaceHttpData.HttpDataType.FileUpload) {
                    FileUpload fileUpload = (FileUpload) data;
                    if (fileUpload.isCompleted()) {
                        byte[] bytes = readByteBufToBytes(fileUpload.getByteBuf());

                        MultipartFile multipartFile = new CustomMultipartFile(
                                bytes,
                                fileUpload.getName(),
                                fileUpload.getFilename(),
                                fileUpload.getContentType() != null ? fileUpload.getContentType() : "application/octet-stream"
                        );
                        responseFiles.add(multipartFile);

                        log.info("Decoded multipart file: field={}, filename={}, size={}, type={}",
                                fileUpload.getName(), fileUpload.getFilename(), fileUpload.length(), fileUpload.getContentType());
                    }
                }
            }

            if (!responseFiles.isEmpty()) {
                payload.setHeader(PayloadHeaders.HTTP_RESPONSE_MULTIPART, responseFiles);
            }
        } finally {
            decoder.destroy();
        }
    }

    /**
     * Processes a single file response (octet-stream) and standardizes it as a Multipart object in the Payload.
     */
    public static void processSingleFileResponse(ByteBuf content, String contentDisposition, Payload payload) {
        String fileName = "unknown.bin";
        String[] parts = contentDisposition.split("filename=");
        if (parts.length > 1) {
            fileName = parts[1].replace("\"", "").trim();
        }

        byte[] fileData = new byte[content.readableBytes()];
        content.readBytes(fileData);

        // Standardize single files as a list to maintain uniform downstream processing
        MultipartFile singleFile = new CustomMultipartFile(fileData, "file", fileName, "application/octet-stream");
        List<MultipartFile> fileList = new ArrayList<>();
        fileList.add(singleFile);

        payload.setHeader(PayloadHeaders.HTTP_RESPONSE_MULTIPART, fileList);

        log.info("Single file response detected. Filename: [{}], Size: [{} bytes]", fileName, fileData.length);
    }

    /**
     * Efficiently writes a multipart file directly to the local disk using NIO Channels.
     */
    public static File multipartLocalDownload(File dir, MultipartFile multipartFile) throws IOException {
        File destFile = new File(dir, multipartFile.getOriginalFilename());

        try (ReadableByteChannel rbc = Channels.newChannel(multipartFile.getInputStream());
             FileOutputStream fos = new FileOutputStream(destFile);
             FileChannel fileChannel = fos.getChannel()) {

            // Zero-copy transfer optimization
            fileChannel.transferFrom(rbc, 0, Long.MAX_VALUE);
        }

        log.info("File successfully downloaded to disk: {}", destFile.getAbsolutePath());
        return destFile;
    }
}
