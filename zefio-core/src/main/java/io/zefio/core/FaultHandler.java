package io.zefio.core;

import io.zefio.core.beans.FlowSyncBridge;
import io.zefio.core.payload.Payload;

/**
 * Interface for terminal error handling logic.
 * Provides a default mechanism to notify pending waiters in the SyncBridge
 * when an exception occurs, preventing resource leaks.
 */
public interface FaultHandler extends GatewayInterceptor {

    /**
     * Notifies the SyncBridge to release any pending WaitFilters associated with this transaction.
     * Invoked when a terminal error occurs in either CPU or I/O error filters.
     */
    default void notifyBridgeIfPending(Payload payload, FlowSyncBridge syncBridge) {
        if (syncBridge == null) return;

        String tid = payload.getTrxID();

        if (tid != null && payload.hasException()) {
            // Wake up the waiter with the captured exception
            syncBridge.completeExceptionally(tid, payload.getThrowable());
        }
    }
}
