// server/api/deploy.post.ts
import { defineEventHandler, readBody } from 'h3'

export default defineEventHandler(async (event) => {
  const body = await readBody(event)

  if (!body?.yaml) {
    return { status: 400, message: 'YAML content is required.' }
  }

  try {
    console.log(`[Zefio CP] Initiating Cluster-wide Hot-Reload...`)

    // Note: Nitro auto-imports broadcastToCluster from utils/cluster.ts
    // Broadcast the Hot-Reload command to all connected DP nodes via Redis
    const activeCount = await broadcastToCluster({
      type: 'command',
      action: 'hot-reload',
      payload: { yaml: body.yaml }
    })

    if (activeCount === 0) {
      throw new Error("No active DP nodes connected. Cannot deploy.")
    }

    return { 
      status: 200, 
      message: `Pipeline deployed successfully via Redis to ${activeCount} active node(s).` 
    }
    
  } catch (error: any) {
    console.error('[Zefio CP] Deployment failed:', error.message)
    return { 
      status: 500, 
      message: error.message 
    }
  }
})