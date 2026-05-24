import { defineEventHandler, readBody } from 'h3'
import { useStorage } from '#imports'

export default defineEventHandler(async (event) => {
  const body = await readBody(event)
  const storage = useStorage('db')

  console.log('[Zefio CP] RAW Incoming Body:', JSON.stringify(body, null, 2))
  
  if (!body || !body.globals || !Array.isArray(body.plugins)) {
    console.warn('[Zefio CP] ❌ Handshake failed: Invalid template payload received.')
    return { status: 400, message: 'Invalid payload.' }
  }

  try {
    const timestamp = new Date().toISOString()
    
    // 1. Log detailed Global Environment counts
    const profileCount = Object.keys(body.globals.profiles || {}).length
    const telegramCount = Object.keys(body.globals.telegrams || {}).length
    const endpointCount = Object.keys(body.globals.endpoints || {}).length

    console.log(`[Zefio CP] 🔄 Bootstrap Handshake Received:`)
    console.log(`   > Profiles: ${profileCount}`)
    console.log(`   > Telegrams: ${telegramCount}`)
    console.log(`   > Endpoints: ${endpointCount}`)
    console.log(`   > Plugins/DTOs: ${body.plugins.length}`)

    // 2. Persist Global Topology
    await storage.setItem('zefio:topology:globals', {
      data: body.globals,
      indexedAt: timestamp
    })

    // 3. Process and Persist Plugin Metadata
    const masterRegistry: any[] = []
    for (const plugin of body.plugins) {
      masterRegistry.push({
        name: plugin.name,
        type: plugin.type,
        className: plugin.className
      })

      await storage.setItem(`zefio:plugin:${plugin.name}`, {
        metadata: { name: plugin.name, type: plugin.type, className: plugin.className },
        dto: plugin.schema || {},
        indexedAt: timestamp
      })
    }

    await storage.setItem('zefio:registry:master', masterRegistry)

    console.log(`[Zefio CP] ✅ Bootstrap Handshake completed successfully at ${timestamp}`)

    return {
      status: 200,
      message: 'Templates successfully synchronized.',
      metrics: { profileCount, telegramCount, endpointCount, pluginCount: body.plugins.length }
    }

  } catch (error: any) {
    console.error('[Zefio CP] ❌ Template sync error:', error.message)
    return { status: 500, message: 'Internal Server Error during sync.' }
  }
})