package io.zefio.gateway.netty.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.zefio.core.common.exception.FlowException;
import io.zefio.core.common.exception.FlowResultStatus;
import io.zefio.core.common.util.FlowErrorUtils;
import io.zefio.gateway.netty.dto.PoolConfig;
import io.zefio.gateway.netty.handler.ChannelConnectionObservable;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.zefio.gateway.netty.NettyMdcConstants.MDC_CONTEXT_KEY;

/**
 * Factory for Netty Channel Pooling.
 * Handles the lifecycle of persistent Upstream connections.
 */
public abstract class AbstractNettyChannelFactory implements PooledObjectFactory<Channel>, PropertyChangeListener {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final Bootstrap bootstrap;
    protected final String host;
    protected final int port;
    protected final long timeoutMillis;
    protected final long intervalMillis;
    protected final AtomicBoolean running;

    public AbstractNettyChannelFactory(Bootstrap bootstrap, String host, int port, long timeoutMillis, PoolConfig poolConfig, AtomicBoolean running) {
        this.bootstrap = bootstrap;
        this.host = host;
        this.port = port;
        this.timeoutMillis = timeoutMillis;
        this.intervalMillis = poolConfig.getPoolReconnectIntervalMillis();
        this.running = running;
    }

    @Override
    public PooledObject<Channel> makeObject() throws Exception {
        // Initial creation of a persistent channel
        Channel channel = createPersistentChannel();
        return new DefaultPooledObject<>(channel);
    }

    @Override
    public boolean validateObject(PooledObject<Channel> pooledObject) {
        Channel ch = pooledObject.getObject();
        return ch != null && ch.isActive() && ch.isOpen();
    }

    @Override
    public void activateObject(PooledObject<Channel> pooledObject) throws Exception {
        // Perform health check before borrowing from the pool
        if (!validateObject(pooledObject)) {
            throw new FlowException(FlowResultStatus.ALREADY_CLOSED, "Channel is invalid during activation");
        }
    }

    @Override
    public void passivateObject(PooledObject<Channel> pooledObject) throws Exception {
        Channel ch = pooledObject.getObject();

        // [Cleanup] Clear MDC context to prevent data leakage between pooled reuse cycles
        if (ch != null && ch.hasAttr(MDC_CONTEXT_KEY)) {
            ch.attr(MDC_CONTEXT_KEY).set(null);
            log.debug("MDC context cleared from channel [{}] during passivation.", ch.id().asShortText());
        }

        if (ch == null || !ch.isActive()) {
            throw new FlowException(FlowResultStatus.ALREADY_CLOSED, "Channel became invalid during passivation");
        }
    }

    @Override
    public void destroyObject(PooledObject<Channel> pooledObject) throws Exception {
        Channel ch = pooledObject.getObject();
        log.debug("Destroying channel [{}]: isOpen={}, isActive={}",
                ch.id().asShortText(), ch.isOpen(), ch.isActive());

        if (ch != null && ch.isOpen()) {
            ch.close();
        }
    }

    /**
     * Attempts to create a persistent connection for the pool (Fail-Fast).
     * Prevents thread blocking by throwing an immediate exception upon connection failure.
     */
    private Channel createPersistentChannel() throws Exception {
        if (!running.get()) {
            throw new FlowException(FlowResultStatus.SYSTEM_SHUTDOWN, "System is shutting down; aborting channel creation.");
        }

        try {
            ChannelFuture future = bootstrap.connect(host, port);
            boolean completed = future.await(timeoutMillis);

            if (!completed) {
                throw new FlowException(FlowResultStatus.CONNECT_TIMEOUT, "Upstream connection timed out after " + timeoutMillis + "ms");
            }

            if (!future.isSuccess()) {
                Throwable cause = future.cause();
                if (cause != null) {
                    throw FlowErrorUtils.convert(cause);
                } else {
                    throw new FlowException(FlowResultStatus.NETWORK_ERROR, "Upstream connection failed without explicit cause");
                }
            }

            Channel channel = future.channel();
            // Register observer for connection status monitoring
            channel.pipeline().addFirst(new ChannelConnectionObservable(this));
            return channel;

        } catch (Exception e) {
            log.debug("{} Persistent connection attempt failed: {}", getProtocolName(), e.getMessage());
            if (e instanceof FlowException) {
                throw e;
            }
            throw FlowErrorUtils.convert(e);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (!"connected".equals(evt.getPropertyName())) return;

        boolean isConnected = (boolean) evt.getNewValue();
        String protocol = getProtocolName();

        if (isConnected) {
            log.debug("[{}] [{}:{}] Connected.", protocol, host, port);
        } else {
            log.debug("[{}] [{}:{}] Disconnected.", protocol, host, port);
        }
    }

    protected abstract String getProtocolName();
}
