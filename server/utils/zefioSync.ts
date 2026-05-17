// server/utils/zefioSync.ts
import { useStorage } from '#imports'

export const syncAllSchemasFromDP = async () => {
  const config = useRuntimeConfig()
  const storage = useStorage('db')
  const dpUrl = config.dpApiUrl

  console.log(`[Zefio CP] Starting Intelligent Schema Indexing...`)

  try {
    // =========================================================================
    // 1. Synchronize Plugin (Adapter/Interceptor) Metadata & DTOs
    // =========================================================================
    console.log(`[Zefio CP] Phase 1: Syncing Plugin DTOs...`)
    const plugins: any[] = await $fetch(`${dpUrl}/base/config`)
    
    const pluginIndex = plugins.map(p => ({
      name: p.name,
      type: p.type,
      className: p.className
    }))

    await storage.setItem('zefio:registry:master', pluginIndex)

    for (const plugin of plugins) {
      try {
        const dtoSchema = await $fetch(`${dpUrl}/base/config/dto/${plugin.name}`)
        await storage.setItem(`zefio:plugin:${plugin.name}`, {
          metadata: plugin,
          dto: dtoSchema,
          indexedAt: new Date().toISOString()
        })
      } catch (err: any) {
        console.warn(`[Zefio CP] Skip DTO for ${plugin.name}: ${err.message}`)
      }
    }


    // =========================================================================
    // 2. Synchronize Global Topology (Profiles, Telegrams, Endpoints)
    // =========================================================================
    console.log(`[Zefio CP] Phase 2: Syncing Global Environments...`)
    try {
      const globals: any = await $fetch(`${dpUrl}/base/topology/globals`)
      
      // Store the entire Globals object so the AI can reference it
      // (This internally includes maps for profiles, telegrams, and endpoints)
      await storage.setItem('zefio:topology:globals', {
        data: globals,
        indexedAt: new Date().toISOString()
      })
      console.log(`[Zefio CP] Synced Globals: Profiles(${Object.keys(globals.profiles || {}).length}), Telegrams(${Object.keys(globals.telegrams || {}).length})`)
    } catch (err: any) {
      console.warn(`[Zefio CP] Failed to sync Globals: ${err.message}`)
    }


    // =========================================================================
    // 3. Synchronize Runtime Flow Configurations (Saved as-is)
    // =========================================================================
    console.log(`[Zefio CP] Phase 3: Syncing Flow Configurations...`)
    try {
      const flows: any[] = await $fetch(`${dpUrl}/base/topology/flows`)
      
      const flowIndex = flows.map(f => ({ name: f.name }))
      await storage.setItem('zefio:registry:flows', flowIndex) // Flow list index

      for (const flow of flows) {
        // 💡 Architecture Rule: Do not forcefully join Telegrams here.
        // We must preserve the engine's reference structure (e.g., telegram: 'fixed-http-req')
        // to ensure the AI generates accurate and compliant YAML strings.
        await storage.setItem(`zefio:flow:${flow.name}`, {
          data: flow,
          indexedAt: new Date().toISOString()
        })
      }
      console.log(`[Zefio CP] Synced ${flows.length} Flows successfully.`)
    } catch (err: any) {
      console.warn(`[Zefio CP] Failed to sync Flows: ${err.message}`)
    }

    console.log(`[Zefio CP] Intelligent Indexing Completed Successfully.`)
    return {
      status: 'success',
      syncedPlugins: pluginIndex.length
    }

  } catch (error: any) {
    console.error(`[Zefio CP] Critical Sync Failure: ${error.message}`)
    throw error
  }
}