// server/utils/cluster.ts
import { Redis } from 'ioredis'

// Connect to Redis using Environment Variable (Fallback to localhost for open-source users)
const REDIS_URL = process.env.REDIS_URL || 'redis://localhost:6379/0'

// Nuxt Global State & Redis Objects (Auto-imported)
export const redisSub = new Redis(REDIS_URL)
export const redisPub = new Redis(REDIS_URL)
export const clusterNodes = new Map<string, any>()
export const uiPeers = new Set<any>()

// Top-down command dispatcher (e.g., used for Deployments)
export async function broadcastToCluster(commandObj: any): Promise<number> {
  const messageStr = JSON.stringify(commandObj)
  try {
    await redisPub.publish('zefio:commands', messageStr)
    console.log(`[Zefio CP] Broadcasted command '${commandObj.action}' to Redis.`)
    return clusterNodes.size // Returns the estimated number of nodes receiving the hot-reload
  } catch (err) {
    console.error(`[Zefio CP] Failed to publish command to Redis:`, err)
    return 0
  }
}

export function aggregateClusterMetrics() {
  let totalTps = 0, totalFailures = 0, totalUsedHeap = 0, totalMaxHeap = 0, totalCpu = 0
  const nodeCount = clusterNodes.size
  const sedaMap = new Map(), connMap = new Map()

  clusterNodes.forEach((nodeData) => {
    const p = nodeData.payload
    totalTps += (p.tps || 0); totalFailures += (p.totalFailures || 0)
    
    if (p.jvm) {
      totalUsedHeap += (p.jvm.usedHeapMb || 0)
      totalMaxHeap += (p.jvm.maxHeapMb || 0)
      totalCpu += (p.jvm.cpuUsagePercent || 0)
    }

    if (p.sedaPools) {
      p.sedaPools.forEach((pool: any) => {
        if (!sedaMap.has(pool.plugin)) sedaMap.set(pool.plugin, { plugin: pool.plugin, active: 0, max: 0 })
        const g = sedaMap.get(pool.plugin)
        g.active += pool.active; g.max += pool.max
      })
    }
    if (p.connectionPools) {
      p.connectionPools.forEach((pool: any) => {
        const key = `${pool.flow}-${pool.name}`
        if (!connMap.has(key)) connMap.set(key, { name: pool.name, flow: pool.flow, active: 0, max: 0 })
        const g = connMap.get(key)
        g.active += pool.active; g.max += pool.max
      })
    }
  })

  return {
    activeNodes: Array.from(clusterNodes.keys()), 
    jvmStats: {
      usedHeapMb: totalUsedHeap,
      maxHeapMb: totalMaxHeap,
      cpuUsagePercent: nodeCount > 0 ? Math.round(totalCpu / nodeCount) : 0, 
      uptimeMin: 0 
    },
    globalMetrics: { tps: totalTps, totalFailures },
    sedaPools: Array.from(sedaMap.values()),
    connectionPools: Array.from(connMap.values())
  }
}