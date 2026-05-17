package io.zefio.core.payload;

/**
 * Standardized header keys used throughout the SEDA pipeline.
 * Namespaces are categorized by protocol to ensure metadata consistency
 * across different Ingress and Upstream modules.
 */
public final class PayloadHeaders {

    // ==========================================
    // [1] HTTP Standard Header Namespace
    // ==========================================
    public static final String HTTP_REQUEST_PREFIX =         "http.req.";
    public static final String HTTP_RESPONSE_PREFIX =        "http.res.";
    public static final String HTTP_REQUEST_PATH =           HTTP_REQUEST_PREFIX + "path";
    public static final String HTTP_REQUEST_METHOD =         HTTP_REQUEST_PREFIX + "method";
    public static final String HTTP_REQUEST_MULTIPART =      HTTP_REQUEST_PREFIX + "multipart";
    public static final String HTTP_RESPONSE_MULTIPART =     HTTP_RESPONSE_PREFIX + "multipart";

    // ==========================================
    // [2] FILE Transfer Namespace
    // ==========================================
    public static final String FILE_PREFIX =                 "file.";
    public static final String FILE_REQUEST_PREFIX =         FILE_PREFIX + "req.";
    public static final String FILE_RESPONSE_PREFIX =        FILE_PREFIX + "res.";

    // Request attributes for dynamic routing
    public static final String FILE_METHOD =                 FILE_REQUEST_PREFIX + "method";
    public static final String FILE_PATH_DATE_REGX =         FILE_REQUEST_PREFIX + "pathDateRegx";
    public static final String FILE_REMOTE_PATH =            FILE_REQUEST_PREFIX + "remotePath";
    public static final String FILE_LOCAL_PATH =             FILE_REQUEST_PREFIX + "localPath";

    // Results of transfer operations
    public static final String FILE_TRANSFER_RESULTS =       FILE_RESPONSE_PREFIX + "transferResults";

    // ==========================================
    // [3] S3 Compatible Object Storage Namespace
    // ==========================================
    public static final String S3_PREFIX =                   "s3.req.";

    public static final String S3_METHOD =                   S3_PREFIX + "method";
    public static final String S3_BUCKET_NAME =              S3_PREFIX + "bucketName";
    public static final String S3_FILE_KEY =                 S3_PREFIX + "fileKey";
    public static final String S3_TARGET_KEY =               S3_PREFIX + "targetKey";
    public static final String S3_LOCAL_DOWNLOAD_PATH =      S3_PREFIX + "localDownloadPath";
    public static final String S3_LOCAL_UPLOAD_PATH =        S3_PREFIX + "localUploadPath";

    // ==========================================
    // [4] Tmax (JTmax) Namespace
    // ==========================================
    public static final String TMAX_PREFIX =                 "tmax.req.";
    public static final String TMAX_SERVICE_NAME =           TMAX_PREFIX + "serviceName";
    public static final String TMAX_GROUP_NAME =             TMAX_PREFIX + "groupName";
    public static final String TMAX_HANDLER =                TMAX_PREFIX + "handler";

    public static final String TMAX_RESPONSE_PREFIX =        TMAX_PREFIX + "res.";
    public static final String TMAX_STATUS =                 TMAX_RESPONSE_PREFIX + "status";
    public static final String TMAX_TPURCODE =               TMAX_RESPONSE_PREFIX + "tpurcode";

    // ==========================================
    // [5] Tuxedo (Jolt/JNI) Namespace
    // ==========================================
    public static final String TUXEDO_PREFIX =               "tuxedo.";

    // Request Namespace
    public static final String TUXEDO_REQUEST_PREFIX =       TUXEDO_PREFIX + "req.";
    public static final String TUXEDO_SERVICE_NAME =         TUXEDO_REQUEST_PREFIX + "serviceName";

    // Response Namespace
    public static final String TUXEDO_RESPONSE_PREFIX =      TUXEDO_PREFIX + "res.";
    public static final String TUXEDO_TPURCODE =             TUXEDO_RESPONSE_PREFIX + "tpurcode";
    public static final String TUXEDO_STATUS =               TUXEDO_RESPONSE_PREFIX + "status";


    private PayloadHeaders() {
        // Prevent instantiation
    }
}
