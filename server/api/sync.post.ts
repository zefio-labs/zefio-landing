// server/api/sync.post.ts
import { fullSystemSync } from '../utils/sync-manager'

export default defineEventHandler(async (event) => {
  await fullSystemSync() // 수동 동기화 요청 시 전체 리프레시
  return { status: 200, message: 'Sync and Deploy triggered manually.' }
})