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
        statusMessage: `[Zefio CP] Plugin schema for '${pluginName}' not found. Please check if DP sync is completed.`
      })
    }
    
    // Return stored metadata and DTO for AI consumption
    return pluginData
  } 
  
  // [Case 2] Return master registry (all plugins) if no query parameter is provided
  else {
    const masterRegistry: any = await storage.getItem('zefio:registry:master')
    
    if (!masterRegistry) {
      // Failsafe for when DP is still booting or not yet synchronized
      return {
        status: 'syncing',
        message: 'Schema registry is currently empty or syncing with DP.',
        plugins: []
      }
    }

    // Return the index of currently available plugins to the AI
    return {
      status: 'ready',
      count: masterRegistry.length,
      plugins: masterRegistry
    }
  }
})