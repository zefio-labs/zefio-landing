// server/utils/sync-manager.ts
import { pullMetadataFromDp } from './dp-pull' // 이전 단계에서 만든 파일
import { compileAndDeployFlows } from './flow-compiler'

export async function fullSystemSync() {
  console.log('[Zefio CP] 📡 Sync Manager: Start');
  
  try {
    console.log('[Zefio CP] 📡 Step 1: Pulling Metadata from DP...');
    await pullMetadataFromDp();
    
    console.log('[Zefio CP] 📡 Step 2: Compiling Flows...');
    await compileAndDeployFlows();
    
    console.log('[Zefio CP] ✅ Sync Manager: Complete');
  } catch (err) {
    console.error('[Zefio CP] ❌ Sync Manager ERROR:', err);
  }
}