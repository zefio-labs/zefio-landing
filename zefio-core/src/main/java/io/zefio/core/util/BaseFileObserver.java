package io.zefio.core.util;

import java.io.File;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility base class for monitoring file changes.
 * Tracks file timestamps and triggers the onChange event when a modification is detected.
 */
public abstract class BaseFileObserver implements Runnable {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Collection<File> files;
    private final Map<File, Long> timestamps = new HashMap<File, Long>();

    public BaseFileObserver(File file) {
        this(Collections.singletonList(file));
    }

    public BaseFileObserver(Collection<File> files) {
        this.files = files;

        for (File file : files) {
            timestamps.put(file, file.lastModified());
        }
    }

    @Override
    public final void run() {
        File latestFile = null;

        for (File file : files) {
            long originalTimestamp = timestamps.get(file);
            long currentTimestamp = file.lastModified();

            if (originalTimestamp != currentTimestamp) {
                timestamps.put(file, currentTimestamp);
                latestFile = file;
            }
        }

        if (latestFile != null) {
            try {
                log.info("Change detected for file: {}", latestFile.getAbsolutePath());
                onChange(latestFile);
            } catch (Throwable t) {
                log.error("Exception occurred while monitoring file: {}", latestFile.getName(), t);
            }
        }
    }

    /**
     * Triggered when a file modification is detected.
     */
    protected abstract void onChange(File file);
}
