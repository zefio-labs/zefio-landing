package io.zefio.core.common.util;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A wrapper for ExecutorService that implements DisposableBean to ensure
 * a managed and graceful shutdown of threads when the application context is closed.
 */
public class DisposableExecutor implements DisposableBean {
    private final Logger log = LoggerFactory.getLogger(DisposableExecutor.class);
    @Getter
    private final ExecutorService executor;
    private final String name;

    public DisposableExecutor(ExecutorService executor, String name) {
        this.executor = executor;
        this.name = name;
    }

    @Override
    public void destroy() throws Exception {
        log.info("[{}] ⏳ Starting Graceful Shutdown...", name);

        executor.shutdown();

        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("[{}] ⏰ 30s timeout reached. Calling shutdownNow().", name);
                executor.shutdownNow();

                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.error("[{}] ❌ Pool failed to terminate even after shutdownNow().", name);
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("[{}] ✅ Shutdown complete.", name);
    }
}
