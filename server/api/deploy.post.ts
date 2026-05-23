// server/api/deploy.post.ts
import { defineEventHandler, readBody } from 'h3'
import { useRuntimeConfig } from '#imports'

export default defineEventHandler(async (event) => {
  const body = await readBody(event)
  const config = useRuntimeConfig()
  
  if (!body?.yaml) {
    return { status: 400, message: 'YAML content is required.' }
  }

  try {
    console.log(`[Zefio CP] Forwarding Hot-Reload request to Seed DP...`)

    // Seed DP의 검증 및 브로드캐스트 엔드포인트 호출
    const response: any = await $fetch(`${config.dpApiUrl}/base/config/reload`, {
      method: 'POST',
      body: { 
        yaml: body.yaml,
        targetGroup: body.targetGroup || 'main' // 타겟 그룹 전달
      }
    })

    return { status: 200, message: response.message }
    
  } catch (error: any) {
    console.error('[Zefio CP] Deployment failed:', error.message)
    const errorMsg = error.data?.reason || error.message;
    return { status: 500, message: `Engine Validation Failed: ${errorMsg}` }
  }
})