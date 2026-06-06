export async function pullMetadataFromDp() {
  const storage = useStorage('db')
  const dpBaseUrl = process.env.DP_API_URL || 'http://localhost:52001'
  
  console.log('[Zefio CP] 📡 Pulling Registry Metadata from DP...');

  try {
    const fullRegistry = await $fetch(`${dpBaseUrl}/base/config/registry`) as any[]
    
    // 💡 로그 추가: 무엇을 가져왔는지 리스트로 출력
    const pluginNames = fullRegistry.map(p => p.name).join(', ');
    console.log(`[Zefio CP] 🧩 DP Registry Synced. Plugins found (${fullRegistry.length}): [${pluginNames}]`);

    for (const plugin of fullRegistry) {
      await storage.setItem(`zefio:plugin:${plugin.name}`, {
        name: plugin.name,
        type: plugin.type,
        className: plugin.className,
        schema: plugin.schema || {},
        indexedAt: new Date().toISOString()
      })
    }
    await storage.setItem('zefio:registry:master', fullRegistry.map(p => ({ name: p.name, type: p.type, className: p.className })))
    console.log(`[Zefio CP] ✅ Metadata Sync Complete.`);
  } catch (e) {
    console.error('[Zefio CP] ❌ Failed to pull registry:', e);
    throw e;
  }
}