import { useStorage } from '#imports'
import yaml from 'js-yaml'
import { redisPub } from './cluster'
import { compileAndFlattenFlows } from './zefio-compiler' // 💡 Import the shared core compiler engine

/**
 * Core engine to compile local DSL blueprints and deploy them to the cluster.
 * This utility handles:
 * 1. Loading global topology (profiles, telegrams, endpoints).
 * 2. Compiling/Flattening flows by delegating to the shared zefio-compiler utility.
 * 3. Enriching UI/AI cache context with full telegram specs.
 * 4. Broadcasting clean hot-reload payloads with a global telegram lookup matrix to DP.
 */
export async function compileAndDeployFlows() {
  console.log('[Zefio CP] 🛠️ compileAndDeployFlows started');
  const storage = useStorage('db')
  const dslStorage = useStorage('assets:dsl')
  const timestamp = new Date().toISOString()

  // 1. Load Global Topology configurations
  const profileRaw = await dslStorage.getItem('globals/profiles.yaml') as string
  const telegramRaw = await dslStorage.getItem('globals/telegrams.yaml') as string
  const endpointRaw = await dslStorage.getItem('globals/endpoints.yaml') as string

  const globalProfiles = yaml.load(profileRaw) as any
  const globalTelegrams = yaml.load(telegramRaw) as any
  const globalEndpoints = yaml.load(endpointRaw) as any

  // Map-based uniform structure for O(1) JIT lookups
  const mergedGlobals = {
    profiles: globalProfiles?.profiles || {},
    telegrams: globalTelegrams?.telegrams || {},
    endpoints: globalEndpoints?.endpoints || {}
  }

  // Persist global topology for AI/UI context
  await storage.setItem('zefio:topology:globals', { data: mergedGlobals, indexedAt: timestamp })

  // 2. Discover and process flow configuration blueprints
  const allAssetKeys = await dslStorage.getKeys()
  console.log(`[Zefio CP] 🔍 Total Asset Keys discovered:`, allAssetKeys);

  // Supported both slash (/) and colon (:) formats for seamless Nitro virtual path scanning
  const flowKeys = allAssetKeys.filter(key => {
    const normalized = key.replace(/\\/g, '/')
    return (normalized.startsWith('flows/') || normalized.startsWith('flows:')) &&
      (normalized.endsWith('.yaml') || normalized.endsWith('.yml'))
  })

  console.log(`[Zefio CP] 📦 Compiling ${flowKeys.length} flow DSL blueprints...`)
  const flowRegistryNames: string[] = []

  for (const key of flowKeys) {
    const fileContent = await dslStorage.getItem(key) as string
    const parsedFlowConfig = yaml.load(fileContent) as any

    if (!parsedFlowConfig || !parsedFlowConfig.flows) continue

    for (const flow of parsedFlowConfig.flows) {
      if (!flow.name) continue
      flowRegistryNames.push(flow.name)

      // 💡 Centralized Compilation Pass: Mutates the single flow inside the array and serializes the exact DP payload
      const compiledFlowYamlStr = compileAndFlattenFlows([flow], mergedGlobals)

      // Create an isolated Deep Copy enriched with telegramSpec for local UI/AI storage
      const uiFlowContext = JSON.parse(JSON.stringify(flow))
      injectTelegramSpecsForUi(uiFlowContext, mergedGlobals.telegrams)

      // [DEBUG LOG] Detailed structural inspection using the UI context snapshot
      console.log(`\n================================================================`);
      console.log(`[Zefio CP] 🛠️ [Full Flow Definition] Name: ${flow.name}`);
      console.log(`================================================================`);
      // console.log(JSON.stringify(uiFlowContext, null, 2)); 
      // console.log(`================================================================\n`);
      
      // Cache the enriched flow layout locally for active UI and AI consumption
      await storage.setItem(`zefio:flow:${flow.name}`, uiFlowContext)

      // Broadcast clean hot-reload payload to cluster core
      // const bootstrapDeployPayload = {
      //   targetGroup: "main",
      //   action: "hot-reload",
      //   deployId: `BOOTSTRAP-INIT-${flow.name.toUpperCase()}`,
      //   payload: { 
      //       flowsYaml: compiledFlowYamlStr,        // 💡 Pristine, beautiful clean flow definition string
      //       telegrams: mergedGlobals.telegrams    // 💡 Transmitted as a clean, native JSON object tree mapping next to it
      //   }
      // }
      
      // await redisPub.publish('zefio:command', JSON.stringify(bootstrapDeployPayload))
      // console.log(`  > [Auto-Deploy] Pipeline '${flow.name}' deployed successfully.`)
    }
  }

  // Update registry directory tracking metrics
  await storage.setItem('zefio:registry:flows', flowRegistryNames)
  console.log(`[Zefio CP] ✅ Flow compilation and deployment completed at ${timestamp}`)
}

/**
 * Helper tree crawler to inject deep metadata objects specifically for Control Plane UI/AI usage.
 */
function injectTelegramSpecsForUi(node: any, telegrams: any) {
  if (!node) return
  
  if (node.ingress && node.ingress.telegram && telegrams[node.ingress.telegram]) {
    node.ingress.telegramSpec = telegrams[node.ingress.telegram]
  }
  
  if (node.steps && Array.isArray(node.steps)) {
    for (const step of node.steps) {
      if (step.telegram && telegrams[step.telegram]) {
        step.telegramSpec = telegrams[step.telegram]
      }
      injectTelegramSpecsForUi(step, telegrams)
    }
  }
  
  if (node['fallback-steps'] && Array.isArray(node['fallback-steps'])) {
    for (const step of node['fallback-steps']) {
      injectTelegramSpecsForUi(step, telegrams)
    }
  }
  
  if (node.cases && Array.isArray(node.cases)) {
    for (const c of node.cases) {
      if (c.steps) {
        for (const step of c.steps) injectTelegramSpecsForUi(step, telegrams)
      }
    }
  }
  
  if (node.defaultSteps && Array.isArray(node.defaultSteps)) {
    for (const step of node.defaultSteps) {
      injectTelegramSpecsForUi(step, telegrams)
    }
  }
} 



// import { useStorage } from '#imports'
// import yaml from 'js-yaml'
// import { compileAndFlattenFlows } from './zefio-compiler' // 💡 Import the shared core compiler engine

// /**
//  * Core engine to compile local DSL blueprints and pre-warm local cache.
//  * This utility handles:
//  * 1. Loading global topology (profiles, telegrams, endpoints).
//  * 2. Compiling/Flattening flows by delegating to the shared zefio-compiler utility.
//  * 3. Enriching UI/AI cache context with full telegram specs.
//  * 4. Isolating the cold-start phase from active DP nodes to prevent unintended automated push.
//  */
// export async function compileAndDeployFlows() {
//   console.log('[Zefio CP] 🛠️ compileAndDeployFlows started');
//   const storage = useStorage('db')
//   const dslStorage = useStorage('assets:dsl')
//   const timestamp = new Date().toISOString()

//   // 1. Load Global Topology configurations
//   const profileRaw = await dslStorage.getItem('globals/profiles.yaml') as string
//   const telegramRaw = await dslStorage.getItem('globals/telegrams.yaml') as string
//   const endpointRaw = await dslStorage.getItem('globals/endpoints.yaml') as string

//   const globalProfiles = yaml.load(profileRaw) as any
//   const globalTelegrams = yaml.load(telegramRaw) as any
//   const globalEndpoints = yaml.load(endpointRaw) as any

//   // Map-based uniform structure for O(1) JIT lookups
//   const mergedGlobals = {
//     profiles: globalProfiles?.profiles || {},
//     telegrams: globalTelegrams?.telegrams || {},
//     endpoints: globalEndpoints?.endpoints || {}
//   }

//   // Persist global topology for AI/UI context
//   await storage.setItem('zefio:topology:globals', { data: mergedGlobals, indexedAt: timestamp })

//   // 2. Discover and process flow configuration blueprints
//   const allAssetKeys = await dslStorage.getKeys()
//   console.log(`[Zefio CP] 🔍 Total Asset Keys discovered:`, allAssetKeys);

//   // Supported both slash (/) and colon (:) formats for seamless Nitro virtual path scanning
//   const flowKeys = allAssetKeys.filter(key => {
//     const normalized = key.replace(/\\/g, '/')
//     return (normalized.startsWith('flows/') || normalized.startsWith('flows:')) &&
//       (normalized.endsWith('.yaml') || normalized.endsWith('.yml'))
//   })

//   console.log(`[Zefio CP] 📦 Compiling ${flowKeys.length} flow DSL blueprints...`)
//   const flowRegistryNames: string[] = []

//   for (const key of flowKeys) {
//     const fileContent = await dslStorage.getItem(key) as string
//     const parsedFlowConfig = yaml.load(fileContent) as any

//     if (!parsedFlowConfig || !parsedFlowConfig.flows) continue

//     for (const flow of parsedFlowConfig.flows) {
//       if (!flow.name) continue
//       flowRegistryNames.push(flow.name)

//       // 💡 Centralized Compilation Pass: Mutates the single flow inside the array and serializes the exact DP payload
//       const compiledFlowYamlStr = compileAndFlattenFlows([flow], mergedGlobals)

//       // Create an isolated Deep Copy enriched with telegramSpec for local UI/AI storage
//       const uiFlowContext = JSON.parse(JSON.stringify(flow))
//       injectTelegramSpecsForUi(uiFlowContext, mergedGlobals.telegrams)

//       // [DEBUG LOG] Detailed structural inspection using the UI context snapshot
//       console.log(`\n================================================================`);
//       console.log(`[Zefio CP] 🛠️ [Full Flow Definition] Name: ${flow.name}`);
//       console.log(`================================================================`);
//       console.log(JSON.stringify(uiFlowContext, null, 2)); 
//       console.log(`================================================================\n`);
      
//       // Cache the enriched flow layout locally for active UI and AI consumption
//       await storage.setItem(`zefio:flow:${flow.name}`, uiFlowContext)

//       // 💡 Cache the raw compiled standalone YAML configuration string to be ready for explicit UI deployment
//       await storage.setItem(`zefio:compiled:yaml:${flow.name}`, compiledFlowYamlStr)
//       console.log(`  > [Cache Warmer] Pipeline '${flow.name}' compiled and cached locally.`)
//     }
//   }

//   // Update registry directory tracking metrics
//   await storage.setItem('zefio:registry:flows', flowRegistryNames)
//   console.log(`[Zefio CP] ✅ Flow compilation and caching completed at ${timestamp}`)
// }

// /**
//  * Helper tree crawler to inject deep metadata objects specifically for Control Plane UI/AI usage.
//  */
// function injectTelegramSpecsForUi(node: any, telegrams: any) {
//   if (!node) return
  
//   if (node.ingress && node.ingress.telegram && telegrams[node.ingress.telegram]) {
//     node.ingress.telegramSpec = telegrams[node.ingress.telegram]
//   }
  
//   if (node.steps && Array.isArray(node.steps)) {
//     for (const step of node.steps) {
//       if (step.telegram && telegrams[step.telegram]) {
//         step.telegramSpec = telegrams[step.telegram]
//       }
//       injectTelegramSpecsForUi(step, telegrams)
//     }
//   }
  
//   if (node['fallback-steps'] && Array.isArray(node['fallback-steps'])) {
//     for (const step of node['fallback-steps']) {
//       injectTelegramSpecsForUi(step, telegrams)
//     }
//   }
  
//   if (node.cases && Array.isArray(node.cases)) {
//     for (const c of node.cases) {
//       if (c.steps) {
//         for (const step of c.steps) injectTelegramSpecsForUi(step, telegrams)
//       }
//     }
//   }
  
//   if (node.defaultSteps && Array.isArray(node.defaultSteps)) {
//     for (const step of node.defaultSteps) {
//       injectTelegramSpecsForUi(step, telegrams)
//     }
//   }
// }