// server/api/plugins.get.ts
import { defineEventHandler, getQuery, createError } from 'h3'
import { useStorage } from '#imports'

export default defineEventHandler(async (event) => {
  const storage = useStorage('db')
  const query = getQuery(event)
  const pluginName = query.name as string

  // [Case 1] Request specific plugin's DTO schema (used by AI for YAML generation)
  if (pluginName) {
    const pluginData = await storage.getItem(`zefio:plugin:${pluginName}`)
    
    if (!pluginData) {
      throw createError({
        statusCode: 404,
        // 💡 Message updated to reflect the Push-based architecture
        statusMessage: `[Zefio CP] Schema for '${pluginName}' not found. Ensure the Zefio Engine (DP) has completed its Bootstrap Handshake.`
      })
    }
    
    return pluginData
  } 
  
  // [Case 2] Return master registry (all plugins) if no query parameter is provided
  else {
    const masterRegistry: any = await storage.getItem('zefio:registry:master')
    
    if (!masterRegistry) {
      // Failsafe for when DP is still booting and hasn't pushed templates yet
      return {
        status: 'awaiting_push',
        message: 'Registry is empty. Waiting for DP to push Master Templates on startup.',
        plugins: []
      }
    }

    return {
      status: 'ready',
      count: masterRegistry.length,
      plugins: masterRegistry
    }
  }
})