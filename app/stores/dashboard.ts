// app/stores/dashboard.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useDashboardStore = defineStore('dashboard', () => {
  // ==========================================
  // 🚀 [1] Cluster Status (새로 추가된 핵심 변수)
  // ==========================================
  const activeNodes = ref<string[]>([])

  // ==========================================
  // [2] JVM 및 시스템 메트릭 (Cluster Aggregated)
  // ==========================================
  // 주의: 서버는 jvmStats 라는 이름으로 줍니다.
  const jvmStats = ref({ 
    usedHeapMb: 0, 
    maxHeapMb: 0, 
    cpuUsagePercent: 0, 
    uptimeMin: 0 
  })
  
  // ==========================================
  // [3] 전체 시스템 통계 (Cluster TPS, Failures)
  // ==========================================
  const globalMetrics = ref({ 
    tps: 0, 
    totalFailures: 0 
  })
  
  // ==========================================
  // [4] SEDA 내부 단계별 쓰레드 풀 (Aggregated)
  // ==========================================
  const sedaPools = ref<any[]>([])
  
  // ==========================================
  // [5] 대외 연동용 커넥션 풀 (Aggregated)
  // ==========================================
  const connectionPools = ref<any[]>([])
  
  // (참고) queueStats와 filters는 서버의 aggregateClusterMetrics()에서 
  // 아직 합산 로직을 구현하지 않았으므로 초기값만 유지합니다.
  const queueStats = ref<any[]>([])
  const filters = ref<any[]>([])

  // ==========================================
  // [6] 최근 실시간 거래 내역
  // ==========================================
  const transactions = ref<any[]>([])

  /**
   * WS 통신을 통해 수신된 클러스터 데이터를 스토어에 매핑합니다.
   */
  function updateTelemetry(data: any) {
    // 💡 ws.ts에서 { type: 'telemetry', payload: { ... } } 형태로 보내므로
    // 실제 데이터가 들어있는 payload 객체를 추출합니다.
    const telemetry = data.payload || data;

    // 🚀 서버가 보내는 필드명(Key)과 정확히 일치시켜서 할당합니다.
    
    if (telemetry.activeNodes !== undefined) {
      activeNodes.value = telemetry.activeNodes
    }

    if (telemetry.jvmStats) {
      jvmStats.value = telemetry.jvmStats
    }
    
    if (telemetry.globalMetrics) {
      globalMetrics.value = telemetry.globalMetrics
    }
    
    if (telemetry.sedaPools) {
      sedaPools.value = telemetry.sedaPools
    }

    if (telemetry.connectionPools) {
      connectionPools.value = telemetry.connectionPools
    }

    // 서버 로직에 추가되면 사용될 확장 변수들
    if (telemetry.queueStats) {
      queueStats.value = telemetry.queueStats
    }
    if (telemetry.filters) {
      filters.value = telemetry.filters
    }
    
    // 실시간 트랜잭션 개별 수신 로직 (추후 라이브 트레이스 구현 시 사용)
    if (telemetry.recentTransaction) {
      transactions.value.unshift(telemetry.recentTransaction)
      // 최대 20건 유지 (메모리 누수 방지)
      if (transactions.value.length > 20) {
        transactions.value.pop()
      }
    }
  }

  // 🚀 외부(Vue 컴포넌트)에서 사용할 수 있도록 모두 return 해줍니다.
  return { 
    activeNodes,      // <-- 이 부분이 누락되어 있었기 때문에 헤더 UI가 깨졌습니다.
    jvmStats, 
    globalMetrics, 
    sedaPools, 
    queueStats,
    connectionPools, 
    filters,
    transactions, 
    updateTelemetry 
  }
})