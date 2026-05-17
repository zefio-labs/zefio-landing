package io.zefio.gateway.netty.util;

import io.netty.channel.Channel;
import io.zefio.core.payload.Payload;
import io.zefio.core.payload.PayloadBuilder;
import io.zefio.gateway.netty.dto.ResponseMatchingType;
import io.zefio.gateway.netty.transaction.FireAndForgetTxnManager;
import io.zefio.gateway.netty.transaction.ITxnManager;
import io.zefio.gateway.netty.transaction.SessionTxnManager;
import io.zefio.gateway.netty.transaction.TelegramTxnManager;
import org.slf4j.MDC;

import java.util.Map;

import static io.zefio.gateway.netty.NettyMdcConstants.MDC_CONTEXT_KEY;

/**
 * Utility for Netty transaction management and MDC logging orchestration.
 */
public class NettyUtils {

    /**
     * Factory method for creating the appropriate Transaction Manager based on the matching strategy.
     */
    public static ITxnManager<Payload> createCallback(PayloadBuilder builder, String moduleName,
                                                      long timeoutMillis, boolean isTwoWay,
                                                      boolean isClientSend, ResponseMatchingType matchingType) {
        if (!isTwoWay || matchingType == ResponseMatchingType.NONE) {
            return new FireAndForgetTxnManager(moduleName);
        }

        if (matchingType == ResponseMatchingType.SESSION) {
            return new SessionTxnManager(moduleName, timeoutMillis, isClientSend);
        }

        if (matchingType == ResponseMatchingType.TELEGRAM) {
            if (builder == null) {
                throw new IllegalArgumentException("PayloadBuilder is mandatory for TELEGRAM matching mode in: " + moduleName);
            }
            return new TelegramTxnManager<>(moduleName, builder, timeoutMillis, isClientSend);
        }

        throw new IllegalArgumentException("Unknown ResponseMatchingType: " + matchingType);
    }

    /**
     * Restores MDC context from channel attributes and executes the task.
     * Crucial for asynchronous Netty EventLoop threads to maintain log traceability.
     */
    public static void runWithMdc(Channel channel, Runnable runnable) {
        Map<String, String> mdcContext = channel.attr(MDC_CONTEXT_KEY).get();
        Map<String, String> originalMdcContext = MDC.getCopyOfContextMap();

        if (mdcContext != null) {
            MDC.setContextMap(mdcContext);
        } else {
            MDC.clear();
        }

        try {
            runnable.run();
        } finally {
            // Restore original thread state to prevent polluting the shared EventLoop thread
            if (originalMdcContext != null) {
                MDC.setContextMap(originalMdcContext);
            } else {
                MDC.clear();
            }
        }
    }

    /**
     * Executes a task with a provided MDC snapshot.
     */
    public static void runWithMdc(Map<String, String> mdcContext, Runnable runnable) {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();

        if (mdcContext != null) {
            MDC.setContextMap(mdcContext);
        } else {
            MDC.clear();
        }

        try {
            runnable.run();
        } finally {
            if (previousContext != null) {
                MDC.setContextMap(previousContext);
            } else {
                MDC.clear();
            }
        }
    }

    /**
     * Snapshots the current thread's MDC context into the Channel attribute.
     */
    public static void captureMdc(Channel channel) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        if (mdcContext != null) {
            channel.attr(MDC_CONTEXT_KEY).set(mdcContext);
        }
    }
}
