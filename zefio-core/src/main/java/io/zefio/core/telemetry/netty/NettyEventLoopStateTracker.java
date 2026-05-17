package io.zefio.core.telemetry.netty;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import io.netty.util.internal.PlatformDependent;
import lombok.Getter;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Tracks the state of Netty EventLoopGroups, including thread activity,
 * pending tasks, active channels, and direct memory usage.
 */
public class NettyEventLoopStateTracker {
    @Getter
    private final EventLoopGroup eventLoopGroup;

    /** Supplier for retrieving the count of active channels. */
    private final Supplier<Integer> channelCountSupplier;

    private final AtomicInteger totalThreads = new AtomicInteger();
    private final AtomicInteger activeThreads = new AtomicInteger();
    private final AtomicInteger totalPendingTasks = new AtomicInteger();

    private final AtomicInteger activeChannels = new AtomicInteger();
    private final AtomicLong usedDirectMemory = new AtomicLong();

    public NettyEventLoopStateTracker(EventLoopGroup eventLoopGroup, Supplier<Integer> channelCountSupplier) {
        this.eventLoopGroup = eventLoopGroup;
        this.channelCountSupplier = channelCountSupplier;
        refresh();
    }

    /**
     * Synchronizes the tracker with the current state of the Netty environment.
     * Iterates through executors to calculate pending tasks and captures
     * channel activity and direct memory metrics.
     */
    public synchronized void refresh() {
        if (eventLoopGroup == null || eventLoopGroup.isShuttingDown()) return;

        int threads = 0;
        int active = 0;
        int pending = 0;

        if (eventLoopGroup instanceof MultithreadEventLoopGroup) {
            MultithreadEventLoopGroup mtelg = (MultithreadEventLoopGroup) eventLoopGroup;
            threads = mtelg.executorCount();

            for (EventExecutor executor : mtelg) {
                if (executor instanceof SingleThreadEventExecutor) {
                    SingleThreadEventExecutor ste = (SingleThreadEventExecutor) executor;
                    int p = ste.pendingTasks();
                    pending += p;
                    if (p > 0) active++;
                }
            }
        }

        totalThreads.set(threads);
        activeThreads.set(active);
        totalPendingTasks.set(pending);

        // Retrieves active channel count. Ingress usually uses ChannelGroup,
        // while Upstream uses Pool implementations.
        if (channelCountSupplier != null) {
            activeChannels.set(channelCountSupplier.get());
        }

        // Captures the total used Netty Direct Memory in bytes.
        usedDirectMemory.set(PlatformDependent.usedDirectMemory());
    }

    public int getTotalThreads() { return totalThreads.get(); }
    public int getActiveThreads() { return activeThreads.get(); }
    public int getTotalPendingTasks() { return totalPendingTasks.get(); }

    public int getActiveChannels() { return activeChannels.get(); }
    public long getUsedDirectMemory() { return usedDirectMemory.get(); }
}
