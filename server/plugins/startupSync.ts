// server/plugins/startupSync.ts
export default defineNitroPlugin(async (nitroApp) => {
  const maxRetries = 5
  let attempt = 0

  const trySync = async () => {
    try {
      await syncAllSchemasFromDP()
      console.log('[Zefio CP] Initial Sync Success.')
    } catch (e) {
      attempt++
      if (attempt < maxRetries) {
        console.log(`[Zefio CP] DP not ready. Retrying... (${attempt}/${maxRetries})`)
        setTimeout(trySync, 5000)
      } else {
        console.error('[Zefio CP] DP unreachable after max retries.')
      }
    }
  }

  trySync()
})