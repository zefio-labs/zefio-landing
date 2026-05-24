// server/api/deploy.post.ts
import { defineEventHandler, readBody } from 'h3'
import { redisPub } from '../utils/cluster' // Adjust path if necessary

/**
 * Handles Hot-Reload deployment from the UI.
 * Ultimate Decoupling: CP does NOT need to know the DP's IP address.
 * It simply broadcasts the new YAML via Redis Pub/Sub, and all active DPs
 * in the target group will seamlessly hot-swap their pipelines.
 */
export default defineEventHandler(async (event) => {
  const body = await readBody(event)
  
  if (!body || !body.yaml) {
    return { status: 400, message: 'YAML content is required for deployment.' }
  }

  try {
    const targetGroup = body.targetGroup || 'main'
    console.log(`[Zefio CP] Broadcasting Hot-Reload command to cluster group: [${targetGroup}]`)

    // 1. Construct the exact JSON command that DP's ZefioCpRedisCommandListener expects
    const commandPayload = {
      type: "command",
      action: "hot-reload",
      targetGroup: targetGroup,
      payload: {
        yaml: body.yaml
      }
    }

    // 2. Publish directly to the Redis channel
    // DP's Java listener subscribes to "zefio:command"
    await redisPub.publish('zefio:command', JSON.stringify(commandPayload))

    return { 
      status: 200, 
      message: `Deployment broadcasted successfully to group '${targetGroup}' via Redis.` 
    }
    
  } catch (error: any) {
    console.error('[Zefio CP] Redis Broadcast Deployment failed:', error.message)
    return { 
      status: 500, 
      message: `Failed to broadcast deployment to cluster: ${error.message}` 
    }
  }
})