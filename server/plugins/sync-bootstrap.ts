// server/plugins/sync-bootstrap.ts
import { fullSystemSync } from '../utils/sync-manager'
import { useStorage } from '#imports'

export default defineNitroPlugin(async (nitroApp) => {
  console.log('[Zefio CP] 🚀 Plugin sync-bootstrap loaded!');

  fullSystemSync()
    .then(() => console.log('[Zefio CP] ✅ Initial Sync process completed.'))
    .catch((err) => console.error('[Zefio CP] ❌ Critical failure during sync:', err));
});