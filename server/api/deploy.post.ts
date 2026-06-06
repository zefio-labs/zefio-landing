// server/api/deploy.post.ts
import { defineEventHandler, readBody } from 'h3'
import { useStorage } from '#imports'
import { redisPub } from '../utils/cluster'
import { randomUUID } from 'crypto'
import yaml from 'js-yaml'
import { compileAndFlattenFlows } from '../utils/zefio-compiler'

export default defineEventHandler(async (event) => {
  const body = await readBody(event)
  const storage = useStorage('db')
  
  if (!body || !body.yaml) {
    return { status: 400, message: 'YAML content is required for deployment.' }
  }

  const targetGroup = body.targetGroup || 'main'
  const deployId = randomUUID() 
  console.log(`[Zefio CP] Intercepting deployment [${deployId}]. Commencing Control Plane compilation...`)

  let compiledFlowsYamlStr = ''
  let globalTelegramsContext = {}

  try {
    const incomingDsl = yaml.load(body.yaml) as any
    if (!incomingDsl || !incomingDsl.flows) {
      throw new Error("Invalid DSL structure. Root level 'flows' array is mandatory.")
    }

    // Get Active Global Topology Map Context
    const globalsObj = await storage.getItem('zefio:topology:globals') as any
    const mergedGlobals = globalsObj?.data || { profiles: {}, telegrams: {}, endpoints: {} }
    
    // Extract global telegram matrix mapping context
    globalTelegramsContext = mergedGlobals.telegrams || {}

    // Execute Unified Compiler Component Pass -> Returns pristine flattened flows YAML string
    compiledFlowsYamlStr = compileAndFlattenFlows(incomingDsl.flows, mergedGlobals)

  } catch (compileError: any) {
    console.error('[Zefio CP] Pre-deployment compilation failed:', compileError.message)
    return { status: 500, message: `Control Plane Compilation Failure: ${compileError.message}` }
  }

  // 💡 Aligned Package Structure: Strictly decouple processing pipelines from metadata structures
  const commandPayload = {
    type: "command",
    action: "hot-reload",
    targetGroup: targetGroup,
    deployId: deployId, 
    payload: {
      flowsYaml: compiledFlowsYamlStr,       // Pure value-based business logic architecture layout
      telegrams: globalTelegramsContext      // Native structural global metadata schema map
    }
  }

  const subscriber = redisPub.duplicate()
  await subscriber.subscribe('zefio:deploy:status')

  const waitForStatus = new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      subscriber.unsubscribe('zefio:deploy:status').catch(() => {})
      subscriber.quit().catch(() => {})
      reject(new Error('Deployment timed out. No response from DP nodes within 15 seconds.'))
    }, 15000)

    subscriber.on('message', (channel, message) => {
      if (channel === 'zefio:deploy:status') {
        try {
          const statusData = JSON.parse(message)
          if (statusData.deployId === deployId) {
            clearTimeout(timeout)
            subscriber.unsubscribe('zefio:deploy:status').catch(() => {})
            subscriber.quit().catch(() => {})

            if (statusData.status === 'SUCCESS') {
              resolve(statusData)
            } else {
              reject(new Error(`DP Node [${statusData.nodeId}] Error: ${statusData.message}`))
            }
          }
        } catch (err) {
          console.error('[Zefio CP] Error parsing DP status message', err)
        }
      }
    })
  })

  try {
    console.log(`[Zefio CP] Broadcasting Compiled Hot-Reload [${deployId}] to cluster...`)
    await redisPub.publish('zefio:command', JSON.stringify(commandPayload))

    const result: any = await waitForStatus
    return { status: 200, message: `Deployment successful on node ${result.nodeId}.` }
  } catch (error: any) {
    console.error('[Zefio CP] Redis Deployment failed:', error.message)
    subscriber.unsubscribe('zefio:deploy:status').catch(() => {})
    subscriber.quit().catch(() => {})
    return { status: 500, message: error.message }
  }
})