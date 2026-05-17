package io.zefio.core.telemetry.appender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract base for asynchronous JSON file appenders.
 * Offloads file I/O operations to a dedicated worker thread using a BlockingQueue.
 * Each log event is written to a distinct file as defined by the DynamicFileNaming implementation.
 */
public abstract class AbstractAsyncJsonFileAppender<T extends DynamicFileNaming> extends UnsynchronizedAppenderBase<ILoggingEvent> {

    protected final BlockingQueue<T> logQueue = new LinkedBlockingQueue<>(10000);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread workerThread;

    protected static final ObjectMapper objectMapper = new ObjectMapper();

    @Setter
    protected String jsonLogFilePath;

    @Override
    public void start() {
        if (this.jsonLogFilePath == null || this.jsonLogFilePath.trim().isEmpty()) {
            addError("[JSON Appender] Directory Path is not configured.");
            return;
        }

        try {
            Path dirPath = Paths.get(this.jsonLogFilePath);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            running.set(true);
            workerThread = new Thread(this::processQueue, getWorkerName());
            workerThread.setDaemon(true);
            workerThread.start();

            super.start();
            addInfo("[JSON Appender] Initialized. Output Directory: " + this.jsonLogFilePath);
        } catch (Exception e) {
            addError("Failed to start JSON Appender", e);
        }
    }

    /**
     * Filters ERROR level events and enqueues the transformed DTO for asynchronous writing.
     */
    @Override
    protected void append(ILoggingEvent event) {
        if (!isStarted() || event.getLevel().levelInt < Level.ERROR_INT) return;

        T dto = convertToDto(event);

        if (dto != null) {
            if (!logQueue.offer(dto)) {
                addWarn("JSON Log Queue is full. Dropping log event.");
            }
        }
    }

    /**
     * Implementation for converting a Logback event to the specific DTO type.
     */
    protected abstract T convertToDto(ILoggingEvent event);

    /**
     * Returns the name for the dedicated worker thread.
     */
    protected abstract String getWorkerName();

    /**
     * Internal loop for processing the log queue and writing files to the disk.
     */
    private void processQueue() {
        Path baseDir = Paths.get(this.jsonLogFilePath);

        while (running.get() || !logQueue.isEmpty()) {
            try {
                List<T> batch = new ArrayList<>();
                T first = logQueue.poll(2, TimeUnit.SECONDS);

                if (first != null) {
                    batch.add(first);
                    logQueue.drainTo(batch, 99);

                    for (T dto : batch) {
                        String dynamicFileName = dto.generateFileName();
                        Path targetFile = baseDir.resolve(dynamicFileName);

                        try (BufferedWriter writer = Files.newBufferedWriter(
                                targetFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                            writer.write(objectMapper.writeValueAsString(dto));
                            writer.flush();
                        } catch (Exception fileEx) {
                            addError("Failed to write dynamic file: " + targetFile, fileEx);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                addError("JSON Queue Processing Error", e);
            }
        }
    }

    @Override
    public void stop() {
        running.set(false);
        if (workerThread != null) workerThread.interrupt();
        super.stop();
    }
}
