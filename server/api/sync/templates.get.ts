import { defineEventHandler } from 'h3'
import { useStorage } from '#imports'

/**
 * Retrieves the latest synchronized Master Templates for UI inspection.
 */
export default defineEventHandler(async (event) => {
  try {
    const storage = useStorage('db')

    // Fetch from Nitro Storage
    const registry = await storage.getItem('zefio:registry:master')
    const globals = await storage.getItem('zefio:topology:globals')

    if (!registry || !globals) {
      return {
        status: 404,
        // 메시지 수정: DP의 Handshake가 아니라 시스템 Sync를 호출하라는 의미로 변경
        message: 'No templates found. Please trigger a System Sync to pull metadata from DP.',
        data: null
      }
    }

    return {
      status: 200,
      plugins: registry,
      globals: globals
    }

  } catch (error: any) {
    return {
      status: 500,
      message: 'Failed to retrieve templates from CP storage.',
      error: error.message
    }
  }
})