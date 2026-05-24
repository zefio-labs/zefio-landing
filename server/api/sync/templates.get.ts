// server/api/sync/templates.get.ts
import { defineEventHandler } from 'h3'
import { useStorage } from '#imports'

/**
 * Retrieves the latest synchronized Master Templates for UI inspection.
 */
export default defineEventHandler(async (event) => {
  try {
    const storage = useStorage('db')
    
    // Fetch from Nitro Storage
    const registry = await storage.getItem('zefio:registry:master')
    const globals = await storage.getItem('zefio:topology:globals')

    if (!registry || !globals) {
      return {
        status: 404,
        message: 'No templates found. Ensure Zefio Engine (DP) is running and has completed the Bootstrap Handshake.',
        data: null
      }
    }

    return {
      status: 200,
      plugins: registry,
      globals: globals
    }

  } catch (error: any) {
    return {
      status: 500,
      message: 'Failed to retrieve templates from CP storage.',
      error: error.message
    }
  }
})