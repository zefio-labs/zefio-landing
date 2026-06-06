import { useState } from '#imports'
import { computed } from 'vue'

export const useTranslations = () => {
  const locale = useState<'ko' | 'en'>('locale', () => 'ko')

  const messages = {
    ko: {
      nav: { opensource: 'GitHub', console: 'Console' },
      hero: {
        badge: 'Zefio Engine 0.9.0 - Public Beta',
        title1: '초고속 데이터 스트리밍,',
        title2: '지능형 이벤트 메시',
        subtitle: 'AI 모델 라우팅부터 대규모 IoT 데이터 수집까지. SEDA 아키텍처 기반의 고성능 엔진과 AI 컨트롤 플레인이 모든 트래픽을 지휘합니다.',
        btnCompare: '시작하기',
        btnGithub: '오픈소스 보기'
      },
      feat: {
        adapterTitle1: '무한한 확장성,',
        adapterTitle2: 'Zero-Downtime 플러그인',
        adapterDesc: '클라우드 네이티브 환경에 최적화된 모듈러 아키텍처. HTTP, TCP 소켓부터 Kafka, MQTT 스트림까지 다양한 프로토콜 어댑터를 시스템 중단 없이 즉시 교체하고 확장하세요.',
        adapterPoints: ['실시간 플러그인 교체', '엣지 노드 및 독립적 스케일아웃', '커스텀 어댑터 SDK 지원']
      },
      footer: { tagline: 'Next-Gen SEDA Integration. Zefio Labs.' },
      architect: {
        title: 'Pipeline Designer',
        subtitle: '산업별 시나리오에 맞춰 Zefio 통합 플로우를 설계하고 배포하세요.',
        assistant: 'Zefio AIOps Assistant',
        syncBtn: 'SYNC',
        syncingBtn: 'SYNCING...',
        syncStatus: { success: 'Context Synced', error: 'Sync Failed', awaiting: 'Awaiting Sync' },
        promptPlaceholder: '위의 프리셋을 선택하거나 요구사항을 직접 입력하세요... (예: TCP 트래픽을 카프카로 라우팅)',
        generateBtn: 'Pipeline YAML 생성',
        generatingBtn: 'AI Architect 설계 중...',
        copyBtn: '복사',
        deployBtn: '엔진으로 전송',
        deployingBtn: '배포 중...',
        configTitle: '생성된 설정값',
        presets: [
          {
            id: 'ai-ops',
            industry: 'AI & MLOps',
            icon: 'ph:robot-duotone',
            title: 'Multi-LLM Router',
            desc: '자동 Fail-Fast 보상 기능을 갖춘 OpenAI 및 Gemini 병렬 실행 라우터입니다.',
            prompt: '수신된 프롬프트 토큰을 OpenAI 및 Gemini API 업스트림으로 병렬 라우팅하세요. 특정 프로바이더에서 2000ms 이상의 지연이 발생할 경우, 즉시 FAIL_FAST를 트리거하고 트래픽을 로컬 Ollama 백업 모델로 전환하세요.'
          },
          {
            id: 'iot-edge',
            industry: 'IoT & 모빌리티',
            icon: 'ph:cpu-duotone',
            title: 'Edge Stream Collector',
            desc: '원시 MQTT/센서 텔레메트리 수집을 위해 설계된 고처리량 TCP 파이프라인입니다.',
            prompt: '1883 포트에서 리스닝하는 고동시성 TCP 서버 파이프라인을 생성하세요. 원시 바이트코드 텔레메트리 스트림을 파싱하고, 지능형 중복 제거 필터 스테이지를 적용한 뒤 즉시 HTTP 업스트림 엔드포인트로 팬아웃(Fan-out)하세요.'
          },
          {
            id: 'e-commerce',
            industry: '이커머스',
            icon: 'ph:shopping-cart-duotone',
            title: 'Spike Traffic Buffer',
            desc: '대규모 결제 플래시 세일을 위한 비동기 백프레셔(Backpressure) 큐 컨트롤러입니다.',
            prompt: '8080 포트에서 주문 결제 요청을 처리할 HTTP 인그레스 풀을 구성하세요. 다운스트림 데이터베이스 쓰기 속도를 조절하기 위해 SEDA 스테이징 메모리 버퍼를 통합하고, 내장 메모리 큐를 활용한 비동기 백프레셔 정책을 강제하세요.'
          },
          {
            id: 'gaming',
            industry: '게임 라이브옵스',
            icon: 'ph:game-controller-duotone',
            title: 'Live Telemetry Splitter',
            desc: '유저 상태 및 수익화 로그 추적을 위한 동적 실시간 라우팅 엔진입니다.',
            prompt: '웹소켓을 통해 글로벌 플레이어 활동 이벤트를 수집하세요. SpEL 표현식을 사용해 페이로드 콘텐츠를 동적으로 평가하고, 트랜잭션 이벤트 패킷은 JDBC 데이터 웨어하우스로, 진단 로그는 HTTP 웹훅 엔드포인트로 라우팅하세요.'
          }
        ]
      },
      telemetry: {
        title: '클러스터 텔레메트리',
        subtitle: '활성 코어 엔진 전체의 실시간 집계 스트림 처리 지표입니다.',
        enginesActive: '활성 엔진',
        totalHeap: '총 힙 메모리',
        avgCpu: '평균 CPU 사용률',
        status: '상태',
        totalTps: '총 처리량 (TPS)',
        errors: '에러 수',
        sedaPools: '이벤트 메시 스테이지 풀 (SEDA)',
        connections: '엣지 및 업스트림 연결',
        noData: '활성 이벤트 메시 스테이지가 없습니다.'
      }
    },
    en: {
      nav: { opensource: 'GitHub', console: 'Console' },
      hero: {
        badge: 'Zefio Engine 0.9.0 - Public Beta',
        title1: 'High-Throughput Streaming,',
        title2: 'Intelligent Event Mesh',
        subtitle: 'From AI model routing to massive IoT ingestion. The high-performance SEDA engine and AI Control Plane orchestrate all your traffic.',
        btnCompare: 'Get Started',
        btnGithub: 'View Open Source'
      },
      feat: {
        adapterTitle1: 'Infinite Scalability,',
        adapterTitle2: 'Zero-Downtime Plugins',
        adapterDesc: 'Modular architecture optimized for cloud-native environments. Hot-swap various protocol adapters like HTTP, TCP, Kafka, and MQTT with zero downtime.',
        adapterPoints: ['Real-time Plugin Hot-Swap', 'Edge Node & Independent Scale-out', 'Custom Adapter SDK Support']
      },
      footer: { tagline: 'Next-Gen SEDA Integration. Zefio Labs.' },
      architect: {
        title: 'Pipeline Designer',
        subtitle: 'Design and deploy Zefio integration flows using Schema-Aware AI across various industries.',
        assistant: 'Zefio AIOps Assistant',
        syncBtn: 'SYNC',
        syncingBtn: 'SYNCING...',
        syncStatus: { success: 'Context Synced', error: 'Sync Failed', awaiting: 'Awaiting Sync' },
        promptPlaceholder: 'Select a preset template above or enter your custom requirements here... (e.g., Route incoming TCP traffic to Kafka)',
        generateBtn: 'Generate Pipeline YAML',
        generatingBtn: 'Consulting AI Architect...',
        copyBtn: 'COPY',
        deployBtn: 'PUSH TO ENGINE',
        deployingBtn: 'DEPLOYING...',
        configTitle: 'Generated Configuration',
        presets: [
          {
            id: 'ai-ops',
            industry: 'AI & MLOps',
            icon: 'ph:robot-duotone',
            title: 'Multi-LLM Router',
            desc: 'Parallel execution router for OpenAI and Gemini with automatic fail-fast compensation.',
            prompt: 'Route incoming prompt tokens parallelly to OpenAI and Gemini API upstreams. If any provider experiences latency over 2000ms, immediately trigger FAIL_FAST and shift traffic to the local Ollama backup model.'
          },
          {
            id: 'iot-edge',
            industry: 'IoT & Mobility',
            icon: 'ph:cpu-duotone',
            title: 'Edge Stream Collector',
            desc: 'High-throughput TCP pipeline designed for raw MQTT/sensor telemetry ingestion.',
            prompt: 'Create a high-concurrency TCP server pipeline listening on Port 1883. Parse raw bytecode telemetry streams, apply an intelligent deduplication filter stage, and immediately fan-out to an HTTP upstream endpoint.'
          },
          {
            id: 'e-commerce',
            industry: 'E-Commerce',
            icon: 'ph:shopping-cart-duotone',
            title: 'Spike Traffic Buffer',
            desc: 'Asynchronous backpressure queue controller for mass checkout flash sales.',
            prompt: 'Build an HTTP ingress pool for order checkout requests on Port 8080. Integrate a SEDA staging memory buffer to throttle downstream database writes via internal memory buffers, enforcing an asynchronous backpressure policy.'
          },
          {
            id: 'gaming',
            industry: 'Game Live-Ops',
            icon: 'ph:game-controller-duotone',
            title: 'Live Telemetry Splitter',
            desc: 'Dynamic real-time routing engine for tracking user state and monetization logs.',
            prompt: 'Ingest global player activity events via WebSockets. Dynamically evaluate payload contents using SpEL expressions: route transactional event packets to JDBC data warehouses and diagnostic logs to an HTTP webhook endpoint.'
          }
        ]
      },
      telemetry: {
        title: 'Cluster Telemetry',
        subtitle: 'Real-time aggregated stream processing metrics across all active core engines.',
        enginesActive: 'Engines Active',
        totalHeap: 'Total Heap Memory',
        avgCpu: 'Avg CPU Load',
        status: 'Status',
        totalTps: 'Total TPS',
        errors: 'Errors',
        sedaPools: 'Event Mesh Stage Pools (SEDA)',
        connections: 'Edge & Upstream Connections',
        noData: 'No active event mesh stages across the cluster.'
      }
    }
  }

  const t = computed(() => messages[locale.value])
  const setLocale = (newLocale: 'ko' | 'en') => { locale.value = newLocale }

  return { locale, t, setLocale }
}