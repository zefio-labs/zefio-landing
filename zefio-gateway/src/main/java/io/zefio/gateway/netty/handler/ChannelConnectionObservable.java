package io.zefio.gateway.netty.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * An observable handler that notifies listeners of channel connectivity changes.
 * Primarily used by the connection pool to track the health of persistent Upstream channels.
 */
public class ChannelConnectionObservable extends ChannelInboundHandlerAdapter {
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);

    public ChannelConnectionObservable(PropertyChangeListener listener) {
        addListener(listener);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // Notify listeners that the connection is now established
        support.firePropertyChange("connected", null, true);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // Notify listeners that the connection has been lost
        support.firePropertyChange("connected", null, false);
        super.channelInactive(ctx);
    }

    public void addListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }
}
