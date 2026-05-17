package io.zefio.gateway.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.BaseIoInterceptor;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.PayloadHeaders;
import io.zefio.core.payload.util.BytesUtils;
import io.zefio.gateway.http.dto.FileDownloadFromMultipartValues;
import io.zefio.gateway.http.util.HttpUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Interceptor that extracts a directory path from a fixed offset in the payload body
 * and downloads attached MultipartFiles to that location.
 */
public class FileDownloadFromMultipart extends BaseIoInterceptor {

    private final int dirStart;
    private final int dirLength;
    private final boolean returnJson;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FileDownloadFromMultipart(PluginContext context) {
        super(context);
        FileDownloadFromMultipartValues values = yamlMapper.convertValue(context.getContext(), FileDownloadFromMultipartValues.class);

        this.dirStart = values.getDirStart();
        this.dirLength = values.getDirLength();
        this.returnJson = values.isReturnJson();
    }

    @Override
    public String getDescription() {
        return "Extracts directory path from payload bytes and downloads MultipartFiles.";
    }

    @Override
    public Payload blockingProcessInternal(Payload payload) throws FlowException {
        try {
            Charset encoding = payload.getCurrentEncoding();

            // Extract the target directory path from the specified offset in the payload body
            byte[] dirBytes = BytesUtils.bytesOffsetCopy(payload.getBody(), this.dirStart, this.dirLength);
            File dir = new File(new String(dirBytes, encoding).trim());

            log.info("Target download directory: [{}]", dir.getAbsolutePath());

            // Retrieve the list of MultipartFiles using standard PayloadHeaders
            @SuppressWarnings("unchecked")
            List<MultipartFile> fileList = (List<MultipartFile>) payload.getHeader(PayloadHeaders.HTTP_REQUEST_MULTIPART);

            if (fileList == null || fileList.isEmpty()) {
                throw new FlowException(FlowResultStatus.MESSAGE_FORMAT_ERROR, "No MultipartFile data found in the payload headers under HTTP_REQUEST_MULTIPART.");
            }

            List<File> files = new ArrayList<>();

            // Iterate and download each file to the target directory
            for (MultipartFile file : fileList) {
                files.add(HttpUtils.multipartLocalDownload(dir, file));
            }

            // Optionally format the response body as a JSON result containing downloaded file paths
            if (returnJson) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("files", files.stream()
                        .map(File::getAbsolutePath)
                        .collect(Collectors.toList()));

                byte[] jsonBytes = objectMapper.writeValueAsBytes(result);
                payload.setBody(jsonBytes);
            }

            return payload;
        } catch (Exception e) {
            log.error("Unexpected error in {}: {}", e.getClass().getSimpleName(), e.getMessage());
            throw new FlowException(e, FlowResultStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
