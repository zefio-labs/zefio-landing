// server/api/sync.post.ts
import { defineEventHandler } from 'h3'
import { broadcastToCluster } from '../utils/cluster'

/**
 * Handles UI requests to manually synchronize schemas.
 * Since the architecture is now Push-based (Template-Driven), this acts
 * as a fallback command to trigger a cluster-wide metadata re-push.
 */
export default defineEventHandler(async (event) => {
  try {
    // Optional: If you implement a listener in DP for 'push-templates'
    // you can broadcast this command. Otherwise, just return the info message.
    await broadcastToCluster({
      type: 'command',
      action: 'push-templates',
      targetGroup: 'all'
    })

    return {
      status: 200,
      message: 'Sync request broadcasted. DP engines will re-push their Master Templates automatically.',
    }
  } catch (error: any) {
    return {
      status: 500,
      message: 'Manual sync command failed.',
      error: error.message
    }
  }
})