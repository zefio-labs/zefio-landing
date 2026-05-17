// server/api/ws.ts
import { aggregateClusterMetrics } from '../utils/cluster'

// DP pushes metrics every 3 seconds (3000ms).
// We set the TTL to 12 seconds (allowing ~4 missed heartbeats).
// This generous buffer prevents UI flickering caused by temporary Wi-Fi latency on edge devices like Raspberry Pi.
const NODE_TTL_MS = 12000;
const CLEANUP_INTERVAL_MS = 4000;

// 1. Dead Node Cleanup Scheduler (Heartbeat Monitor)
setInterval(() => {
  const now = Date.now()
  let topologyChanged = false

  clusterNodes.forEach((data, nodeId) => {
    if (now - data.lastSeen > NODE_TTL_MS) {
      clusterNodes.delete(nodeId)
      console.log(`[Zefio CP] ❌ Node Offline: ${nodeId} (No heartbeat for ${now - data.lastSeen}ms)`)
      topologyChanged = true // Mark that the cluster topology has changed
    }
  })

  // 🚀 Immediately broadcast the updated topology to the UI if a node drops
  if (topologyChanged && uiPeers.size > 0) {
    const payloadStr = JSON.stringify({ type: 'telemetry', payload: aggregateClusterMetrics() })
    uiPeers.forEach(peer => peer.send(payloadStr))
  }
}, CLEANUP_INTERVAL_MS)

// 2. Redis Subscribe (DP -> CP Metrics Receiver)
redisSub.subscribe('zefio:telemetry', (err) => {
  if (err) console.error('[Zefio CP] Redis Subscribe Error:', err)
  else console.log('[Zefio CP] Successfully Subscribed to telemetry channel.')
})

redisSub.on('message', (channel, message) => {
  if (channel === 'zefio:telemetry') {
    try {
      const data = JSON.parse(message)
      if (data.nodeId) {
        // Upsert node data and update the last seen timestamp
        clusterNodes.set(data.nodeId, {
          lastSeen: Date.now(),
          payload: data.payload
        })

        // Broadcast the real-time aggregated metrics to all connected UI clients
        if (uiPeers.size > 0) {
          const payloadStr = JSON.stringify({ type: 'telemetry', payload: aggregateClusterMetrics() })
          uiPeers.forEach(peer => peer.send(payloadStr))
        }
      }
    } catch (e) {
      console.error('[Zefio CP] Redis Parse Error:', e)
    }
  }
})

// 3. Browser (UI) WebSocket Handler
export default defineWebSocketHandler({
  open(peer) {
    console.log(`[Zefio CP] 🟢 UI Browser Connected: ${peer.id}`)
  },
  message(peer, message) {
    try {
      const data = JSON.parse(message.text())
      if (data.type === 'ui_subscribe') {
        uiPeers.add(peer)
        console.log(`[Zefio CP] UI Subscribed. Active UIs: ${uiPeers.size}`)
        
        // Send the current cluster state immediately upon connection
        peer.send(JSON.stringify({ type: 'telemetry', payload: aggregateClusterMetrics() }))
      }
    } catch (e) {
      console.error('[Zefio CP] WS Message Error:', e)
    }
  },
  close(peer) {
    console.log(`[Zefio CP] 🔴 UI Browser Disconnected: ${peer.id}`)
    uiPeers.delete(peer)
  }
})