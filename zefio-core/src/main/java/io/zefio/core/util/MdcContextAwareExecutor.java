package io.zefio.core.util;

import org.slf4j.MDC;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * A decorator for ExecutorService that ensures MDC context propagation.
 * It captures the MDC context from the task submission thread and restores it
 * in the execution thread, enabling continuous log tracing across the SEDA pipeline.
 */
public class MdcContextAwareExecutor implements ExecutorService {

    private final ExecutorService delegate;

    public MdcContextAwareExecutor(ExecutorService delegate) {
        this.delegate = delegate;
    }

    /**
     * Wraps a Runnable to capture and inject MDC context.
     */
    private Runnable wrap(final Runnable task) {
        // Capture the MDC snapshot from the calling thread
        final Map<String, String> contextMap = MDC.getCopyOfContextMap();

        return () -> {
            Map<String, String> previousContext = MDC.getCopyOfContextMap();

            if (contextMap != null) {
                MDC.setContextMap(contextMap);
            } else {
                MDC.clear();
            }

            try {
                task.run();
            } finally {
                // Restore or clear context to prevent cross-transaction pollution in thread pools
                if (previousContext != null) {
                    MDC.setContextMap(previousContext);
                } else {
                    MDC.clear();
                }
            }
        };
    }

    /**
     * Wraps a Callable to capture and inject MDC context.
     */
    private <T> Callable<T> wrap(final Callable<T> task) {
        final Map<String, String> contextMap = MDC.getCopyOfContextMap();

        return () -> {
            Map<String, String> previousContext = MDC.getCopyOfContextMap();

            if (contextMap != null) {
                MDC.setContextMap(contextMap);
            } else {
                MDC.clear();
            }

            try {
                return task.call();
            } finally {
                if (previousContext != null) {
                    MDC.setContextMap(previousContext);
                } else {
                    MDC.clear();
                }
            }
        };
    }

    // --- Executor Implementation ---

    @Override
    public void execute(Runnable command) {
        delegate.execute(wrap(command));
    }

    // --- ExecutorService Delegation with MDC wrapping ---

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(wrap(task), result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        Collection<? extends Callable<T>> wrappedTasks = tasks.stream()
                .map(this::wrap)
                .collect(Collectors.toList());
        return delegate.invokeAll(wrappedTasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        Collection<? extends Callable<T>> wrappedTasks = tasks.stream()
                .map(this::wrap)
                .collect(Collectors.toList());
        return delegate.invokeAll(wrappedTasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        Collection<? extends Callable<T>> wrappedTasks = tasks.stream()
                .map(this::wrap)
                .collect(Collectors.toList());
        return delegate.invokeAny(wrappedTasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        Collection<? extends Callable<T>> wrappedTasks = tasks.stream()
                .map(this::wrap)
                .collect(Collectors.toList());
        return delegate.invokeAny(wrappedTasks, timeout, unit);
    }

    // --- Standard ExecutorService methods (Direct Delegation) ---

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
}
