package io.zefio.core.common.util;

import org.springframework.web.multipart.MultipartFile;
import java.io.*;

/**
 * A custom implementation of Spring's MultipartFile interface that facilitates
 * the manual creation of file objects from in-memory byte arrays.
 * This is particularly useful for internal file manipulation, testing,
 * or wrapping raw data for Spring-based file handlers.
 */
public class CustomMultipartFile implements MultipartFile {
    private final byte[] input;
    private final String name;
    private final String filename;
    private final String contentType;

    public CustomMultipartFile(byte[] input, String name, String filename, String contentType) {
        this.input = input;
        this.name = name;
        this.filename = filename;
        this.contentType = contentType;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return filename;
    }

    @Override
    public String getContentType() {
        if (contentType != null) return contentType;
        String type = java.net.URLConnection.guessContentTypeFromName(filename);
        return (type != null) ? type : "application/octet-stream";
    }

    @Override
    public byte[] getBytes() { return input; }

    @Override
    public InputStream getInputStream() { return new ByteArrayInputStream(input); }

    @Override
    public boolean isEmpty() { return input == null || input.length == 0; }

    @Override
    public long getSize() { return input.length; }

    @Override
    public void transferTo(File dest) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            fos.write(input);
        }
    }
}
