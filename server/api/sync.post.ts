// server/api/sync.post.ts
import { defineEventHandler } from 'h3'

/**
 * Endpoint to manually trigger a full schema sync from the UI.
 */
export default defineEventHandler(async (event) => {
  try {
    const result = await syncAllSchemasFromDP()
    
    return {
      status: 200,
      message: 'Manual synchronization completed successfully.',
      data: result
    }
  } catch (error: any) {
    return {
      status: 500,
      message: 'Manual sync failed.',
      error: error.message
    }
  }
})