import { defineEventHandler, readBody } from 'h3'
import { useStorage } from '#imports'

/**
 * Synchronization point for Zefio Engine (DP) to register its schema.
 * Usually called by the DP during startup or after a dynamic plugin reload.
 */
export default defineEventHandler(async (event) => {
  try {
    const body = await readBody(event)

    // Validation: Ensure the payload contains basic plugin metadata
    if (!body || !Array.isArray(body.plugins)) {
      return {
        status: 400,
        message: 'Invalid schema payload. Expected { plugins: [...] }'
      }
    }

    const storage = useStorage('db') // Use local filesystem storage defined in nitro
    const timestamp = new Date().toISOString()
    
    // Persist the schema with metadata for AIOps analysis
    await storage.setItem('zefio:schema:latest', {
      updatedAt: timestamp,
      engineVersion: body.engineVersion || 'Zefio 1.0',
      plugins: body.plugins, // List of PluginMeta from ConfigApi
      raw: body
    })

    console.log(`[Zefio CP] Schema synchronized from DP at ${timestamp} (Port: 52001)`)

    return {
      status: 200,
      message: 'Schema successfully registered in Control Plane.',
      updatedAt: timestamp,
      pluginCount: body.plugins.length
    }

  } catch (error: any) {
    console.error('[Zefio CP] Schema sync error:', error.message)
    return {
      status: 500,
      message: 'Internal Server Error during schema registration.'
    }
  }
})