package io.zefio.core.telemetry.appender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import lombok.Setter;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract base for asynchronous database appenders using MyBatis.
 * Manages a dedicated worker thread for batch inserts and a configuration watcher
 * for dynamic SessionFactory reloads.
 */
public abstract class AbstractAsyncDbAppender<T extends BaseErrorLogDto> extends UnsynchronizedAppenderBase<ILoggingEvent> {

    protected final BlockingQueue<T> logQueue = new LinkedBlockingQueue<>(10000);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread workerThread;
    private Thread watcherThread;

    private volatile SqlSessionFactory sessionFactory;

    protected String configLocation = "properties/mybatis-config.xml";
    protected String schemaPropsLocation = "/properties/error-db.properties";

    @Setter
    protected String externalDbAccessPath;

    @Override
    public void start() {
        File prodFile = (this.externalDbAccessPath != null && !this.externalDbAccessPath.trim().isEmpty())
                ? new File(this.externalDbAccessPath) : null;

        if (prodFile == null || !prodFile.exists() || !prodFile.canRead()) {
            addInfo("[DB Appender] Disabled. Configuration file missing: " + this.externalDbAccessPath);
            return;
        }

        try {
            rebuildSessionFactory(prodFile);
            startConfigWatcher(prodFile);

            running.set(true);
            workerThread = new Thread(this::processQueue, getWorkerName());
            workerThread.setDaemon(true);
            workerThread.start();

            super.start();
            addInfo("[DB Appender] Started successfully.");
        } catch (Exception e) {
            addError("Failed to initialize AbstractAsyncDbAppender", e);
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!isStarted() || event.getLevel().levelInt < Level.ERROR_INT) return;

        T dto = convertToDto(event);

        if (dto != null && !logQueue.offer(dto)) {
            addWarn("Error DB Queue is full. Dropping logs to ensure engine stability.");
        }
    }

    protected abstract String getWorkerName();
    protected abstract T convertToDto(ILoggingEvent event);
    protected abstract String getMapperStatement();
    protected abstract void setQueryParameters(Map<String, Object> params, T dto, String tableName);

    /**
     * Rebuilds the MyBatis SqlSessionFactory using merged properties from
     * internal schemas and external access configurations.
     */
    private synchronized void rebuildSessionFactory(File accessPropsFile) throws IOException {
        Properties finalProps = new Properties();

        try (InputStream is = getClass().getResourceAsStream(schemaPropsLocation)) {
            if (is != null) finalProps.load(is);
        }
        try (InputStream is = new FileInputStream(accessPropsFile)) {
            finalProps.load(is);
        }

        try (InputStream is = Resources.getResourceAsStream(configLocation)) {
            SqlSessionFactory newFactory = new SqlSessionFactoryBuilder().build(is, finalProps);
            SqlSessionFactory oldFactory = this.sessionFactory;

            this.sessionFactory = newFactory;
            addInfo("MyBatis SessionFactory reloaded.");

            if (oldFactory != null) {
                CompletableFuture.runAsync(() -> {
                    try {
                        // Grace period before closing the legacy connection pool
                        Thread.sleep(5000);
                        DataSource ds = oldFactory.getConfiguration().getEnvironment().getDataSource();
                        if (ds instanceof PooledDataSource) {
                            ((PooledDataSource) ds).forceCloseAll();
                        }
                    } catch (Exception e) {
                        addError("Error while disposing legacy DataSource", e);
                    }
                });
            }
        }
    }

    /**
     * Internal consumer loop for processing the log queue and executing batch inserts.
     */
    private void processQueue() {
        while (running.get() || !logQueue.isEmpty()) {
            try {
                List<T> batch = new ArrayList<>();
                T first = logQueue.poll(2, TimeUnit.SECONDS);

                if (first != null) {
                    batch.add(first);
                    logQueue.drainTo(batch, 99);

                    String tableName = "ERROR_LOG_TABLE";
                    if (sessionFactory != null) {
                        Properties vars = sessionFactory.getConfiguration().getVariables();
                        if (vars != null) tableName = vars.getProperty("error.table.name", tableName);
                    }

                    try (SqlSession session = sessionFactory.openSession(ExecutorType.BATCH)) {
                        for (T dto : batch) {
                            Map<String, Object> params = new HashMap<>();
                            setQueryParameters(params, dto, tableName);
                            session.insert(getMapperStatement(), params);
                        }
                        session.commit();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                addError("Database batch insert failed", e);
            }
        }
    }

    /**
     * Monitors the external property file for changes to trigger a SessionFactory rebuild.
     */
    private void startConfigWatcher(File watchFile) {
        watcherThread = new Thread(() -> {
            Path targetPath = watchFile.toPath();
            Path dir = targetPath.getParent();
            if (dir == null || !Files.exists(dir)) return;

            try (WatchService ws = FileSystems.getDefault().newWatchService()) {
                dir.register(ws, StandardWatchEventKinds.ENTRY_MODIFY);

                while (running.get()) {
                    WatchKey key = ws.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (((Path) event.context()).getFileName().toString().equals(targetPath.getFileName().toString())) {
                            Thread.sleep(1000);
                            rebuildSessionFactory(watchFile);
                        }
                    }
                    if (!key.reset()) break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                addError("WatchService failure", e);
            }
        }, "DbConfig-Watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    @Override
    public void stop() {
        running.set(false);
        if (workerThread != null) workerThread.interrupt();
        if (watcherThread != null) watcherThread.interrupt();

        if (sessionFactory != null) {
            DataSource ds = sessionFactory.getConfiguration().getEnvironment().getDataSource();
            if (ds instanceof PooledDataSource) {
                ((PooledDataSource) ds).forceCloseAll();
            }
        }
        super.stop();
    }
}
