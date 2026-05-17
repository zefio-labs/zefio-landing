package io.zefio.gateway.netty.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.common.util.FlowErrorUtils;
import io.zefio.gateway.netty.dto.PoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * Utility for asynchronous Netty connection management.
 */
public class NettyClientHelper {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final Bootstrap bootstrap;
    protected final String host;
    protected final int port;

    public NettyClientHelper(Bootstrap bootstrap, String host, int port) {
        this.bootstrap = bootstrap;
        this.host = host;
        this.port = port;
    }

    private Channel tryConnect(String ip, int port, long timeoutMillis) throws Exception {
        log.debug("Initiating connection attempt to {}:{}", ip, port);
        ChannelFuture connectFuture = bootstrap.connect(ip, port);

        boolean completed = connectFuture.await(timeoutMillis, TimeUnit.MILLISECONDS);
        if (!completed) {
            throw new TimeoutException("Upstream connection timeout after " + timeoutMillis + "ms");
        }

        if (!connectFuture.isSuccess()) {
            Throwable cause = connectFuture.cause();
            if (cause != null) {
                throw FlowErrorUtils.convert(cause);
            } else {
                throw new FlowException(FlowResultStatus.NETWORK_ERROR, "Upstream connection failed without explicit cause");
            }
        }

        Channel channel = connectFuture.channel();
        log.info("Successfully connected to Upstream: {}", channel);
        return channel;
    }

    /**
     * Performs an asynchronous connection attempt with a retry mechanism.
     */
    public CompletableFuture<Channel> connectOnceWithRetry(PoolConfig poolConfig, Executor sharedIoPool) {
        return CompletableFuture.supplyAsync(() -> {
            int totalAttempts = 1 + poolConfig.getOnceMaxRetries();

            for (int attempt = 1; attempt <= totalAttempts; attempt++) {
                try {
                    return tryConnect(host, port, poolConfig.getOnceTryTimeoutMillis());
                } catch (Exception e) {
                    log.warn("Connection attempt {}/{} failed for {}:{}: {}", attempt, totalAttempts, host, port, e.getMessage());

                    if (attempt == totalAttempts) {
                        // Fail-Fast: Maximum retries reached
                        String detail = "All Upstream connection attempts failed. Last attempt: " + attempt;
                        throw new CompletionException(new FlowException(FlowResultStatus.NETWORK_ERROR, detail));
                    }

                    // Apply exponential back-off before retrying
                    long delay = (long) Math.pow(2, attempt - 1) * poolConfig.getOnceBackoffDelayMillis();
                    try {
                        log.debug("Backing off for {}ms before attempt {}/{}", delay, attempt + 1, totalAttempts);
                        Thread.sleep(Math.min(delay, 30000L));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new CompletionException(new FlowException(ie, FlowResultStatus.INTERRUPTED));
                    }
                }
            }
            throw new FlowException(FlowResultStatus.INTERNAL_SERVER_ERROR, "Critical failure in Netty connection retry loop");
        }, sharedIoPool);
    }
}
