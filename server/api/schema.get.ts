import { defineEventHandler } from 'h3'
import { useStorage } from '#imports'

/**
 * Retrieves the latest synchronized schema for the UI or AI agent.
 */
export default defineEventHandler(async (event) => {
  try {
    const storage = useStorage('db')
    const schema = await storage.getItem('zefio:schema:latest')

    if (!schema) {
      return {
        status: 404,
        message: 'Zefio Engine (DP) has not synchronized yet. Please start the engine on port 52001.',
        data: null
      }
    }

    return {
      status: 200,
      ...schema
    }

  } catch (error: any) {
    return {
      status: 500,
      message: 'Failed to retrieve schema from CP storage.',
      error: error.message
    }
  }
})