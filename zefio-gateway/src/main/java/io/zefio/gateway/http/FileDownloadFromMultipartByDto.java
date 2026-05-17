package io.zefio.gateway.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.BaseIoInterceptor;
import io.zefio.core.factory.PluginContext;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.PayloadHeaders;
import io.zefio.core.payload.util.TransferUtils;
import io.zefio.gateway.http.dto.FileDownloadFromMultipartByDtoValues;
import io.zefio.gateway.http.util.HttpUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Interceptor that downloads MultipartFiles to a directory specified in the DTO configuration,
 * supporting dynamic template variable substitution (e.g., date formats).
 */
public class FileDownloadFromMultipartByDto extends BaseIoInterceptor {

    private final String targetDirectory;
    private final boolean returnJson;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FileDownloadFromMultipartByDto(PluginContext context) {
        super(context);
        FileDownloadFromMultipartByDtoValues values = yamlMapper.convertValue(context.getContext(), FileDownloadFromMultipartByDtoValues.class);
        this.targetDirectory = values.getTargetDirectory();
        this.returnJson = values.isReturnJson();
    }

    @Override
    public String getDescription() {
        return "Downloads MultipartFiles to a DTO-configured directory with dynamic path resolution.";
    }

    @Override
    public Payload blockingProcessInternal(Payload payload) throws FlowException {
        try {
            // Resolve dynamic templates (e.g., {{dateFormat}}) in the target directory string
            String resolvedDirPath = TransferUtils.formConvertor(this.targetDirectory, payload);

            log.info("Target download directory: [{}]", resolvedDirPath);

            File dir = new File(resolvedDirPath);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new FlowException(FlowResultStatus.INTERNAL_SERVER_ERROR, "Failed to create the target directory: " + dir.getAbsolutePath());
            }

            // Retrieve the list of MultipartFiles from standard PayloadHeaders
            @SuppressWarnings("unchecked")
            List<MultipartFile> fileList = (List<MultipartFile>) payload.getHeader(PayloadHeaders.HTTP_REQUEST_MULTIPART);

            if (fileList == null || fileList.isEmpty()) {
                throw new FlowException(FlowResultStatus.MESSAGE_FORMAT_ERROR, "No MultipartFile data found in the payload headers under HTTP_REQUEST_MULTIPART.");
            }

            List<File> files = new ArrayList<>();

            // Iterate and download each file to the resolved directory
            for (MultipartFile file : fileList) {
                files.add(HttpUtils.multipartLocalDownload(dir, file));
            }

            // Optionally format the response body as a JSON result containing downloaded file metadata
            if (returnJson) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("files", files.stream()
                        .map(f -> {
                            Map<String, Object> fileInfo = new HashMap<>();
                            fileInfo.put("path", f.getAbsolutePath());
                            fileInfo.put("size", f.length()); // File size in bytes
                            return fileInfo;
                        })
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
