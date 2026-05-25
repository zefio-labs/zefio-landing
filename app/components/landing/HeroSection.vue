<template>
  <section class="relative pt-40 pb-32 px-6 flex flex-col items-center">
    <div class="absolute inset-0 z-0 flex justify-center items-center pointer-events-none">
      <div class="w-[800px] h-[800px] bg-blue-600/20 rounded-full blur-[120px] absolute top-[-20%]"></div>
      <div class="w-[600px] h-[600px] bg-emerald-600/10 rounded-full blur-[100px] absolute bottom-0"></div>
    </div>
    <div class="absolute inset-0 bg-grid-pattern opacity-50 z-0"></div>
    
    <div class="max-w-4xl mx-auto text-center relative z-10 mb-24 animate-float">
      <div class="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-blue-500/10 border border-blue-500/30 text-blue-300 text-xs font-bold mb-6 backdrop-blur-md">
        <span class="w-2 h-2 rounded-full bg-blue-400 animate-pulse"></span>
        {{ t.hero.badge }}
      </div>
      
      <h1 class="text-6xl md:text-7xl font-black tracking-tighter leading-tight mb-6 text-white break-keep text-glow">
        {{ t.hero.title1 }}<br>
        <span class="text-transparent bg-clip-text bg-gradient-to-r from-blue-400 via-indigo-400 to-emerald-400">
          {{ t.hero.title2 }}
        </span>
      </h1>
      
      <p class="text-lg md:text-xl text-slate-400 mb-10 leading-relaxed max-w-2xl mx-auto break-keep font-light">
        {{ t.hero.subtitle }}
      </p>
    </div>

    <div class="w-full max-w-6xl relative z-20">
      <div class="absolute -inset-1 bg-gradient-to-r from-blue-500/30 to-emerald-500/30 rounded-3xl blur-xl opacity-50"></div>

      <ClientOnly>
        <div class="relative rounded-2xl glass-panel overflow-hidden transform transition-all duration-500 hover:border-blue-500/50">
          
          <div class="flex items-center px-5 py-3 bg-white/5 border-b border-white/5">
            <div class="flex gap-2">
              <div class="w-3 h-3 rounded-full bg-rose-500/80"></div>
              <div class="w-3 h-3 rounded-full bg-amber-500/80"></div>
              <div class="w-3 h-3 rounded-full bg-emerald-500/80"></div>
            </div>
            <span class="ml-4 text-xs font-mono text-slate-500">zefio-engine-core</span>
            <div class="ml-auto flex items-center gap-2 text-[10px] font-mono text-blue-400 bg-blue-500/10 px-2.5 py-1 rounded-full border border-blue-500/20 uppercase tracking-wider">
              <Icon name="ph:cpu-fill" class="w-3 h-3" /> Engine Live
            </div>
          </div>

          <div class="grid lg:grid-cols-2 min-h-[500px]">
            <div class="p-8 flex flex-col border-r border-white/5 bg-black/20">
              
              <div class="mb-8">
                <div class="text-[10px] font-bold text-blue-400 uppercase tracking-widest mb-3 flex items-center gap-2">
                  <Icon name="ph:robot-fill" class="w-4 h-4" /> AI Architect
                </div>
                <div class="font-mono text-sm text-slate-300 leading-relaxed min-h-[60px] break-keep bg-white/5 p-4 rounded-xl border border-white/5 shadow-inner">
                  <span class="text-emerald-400 mr-2">❯</span> 
                  <span>{{ displayedPrompt }}</span><span v-if="phase === 'typing'" class="animate-pulse w-2 h-4 bg-blue-400 inline-block align-middle ml-1"></span>
                </div>
              </div>

              <div class="flex-1 relative flex flex-col items-center justify-center p-6 border border-white/5 rounded-xl bg-[#0a0d14]/50 overflow-hidden">
                <div class="absolute inset-0 bg-grid-pattern opacity-30 pointer-events-none"></div>
                
                <div class="relative w-full max-w-xs flex flex-col gap-5 z-10">
                  <div v-for="(node, index) in currentScenario.nodes" :key="index" class="relative flex flex-col items-center">
                    
                    <div 
                      class="px-5 py-3 rounded-xl border backdrop-blur-md font-mono text-xs shadow-lg transition-all duration-300 w-full text-center flex flex-col items-center justify-center"
                      :class="getNodeClass(index, node)"
                    >
                      <div class="text-[9px] opacity-60 mb-0.5 tracking-widest uppercase">{{ node.type }}</div>
                      <div class="font-bold tracking-tight">{{ node.name }}</div>
                      
                      <Icon v-if="activeNodeIndex === index && phase === 'running'" name="ph:circle-notch-bold" class="absolute -right-2 -top-2 w-5 h-5 animate-spin text-white drop-shadow-[0_0_8px_rgba(255,255,255,0.8)]" />
                    </div>

                    <div v-if="index < currentScenario.nodes.length - 1" class="h-5 w-px relative overflow-hidden my-1 bg-white/10">
                      <div 
                        v-if="activeNodeIndex === index && phase === 'running'"
                        class="absolute top-0 w-full h-full animate-[flowDown_0.5s_linear_infinite] bg-gradient-to-b from-blue-400 to-transparent"
                      ></div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div class="flex flex-col bg-black/40">
              
              <div class="flex-1 p-6 border-b border-white/5 relative overflow-hidden text-slate-300">
                <div class="text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-4 flex justify-between items-center">
                  <span class="flex items-center gap-2"><Icon name="ph:code-block-fill" class="w-4 h-4 text-emerald-400" /> Compiled Pipeline YAML</span>
                </div>
                <div class="bg-[#030712] p-4 rounded-xl border border-white/5 h-[calc(100%-30px)] overflow-auto scrollbar-hide shadow-inner">
                  <pre class="font-mono text-[11px] leading-loose transition-all duration-700" 
                       :class="phase === 'typing' ? 'opacity-0 translate-y-4' : 'opacity-100 translate-y-0'" 
                       v-html="currentScenario.yamlHtml"></pre>
                </div>
              </div>

              <div class="h-60 p-6 flex flex-col relative">
                <div class="text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-3 flex items-center gap-2">
                  <Icon name="ph:terminal-window-fill" class="w-4 h-4 text-rose-400" /> Live Trace Logs
                </div>
                <div class="flex-1 font-mono text-[10px] leading-relaxed overflow-y-auto scrollbar-hide flex flex-col justify-end" ref="logContainer">
                  <div v-for="(log, idx) in visibleLogs" :key="idx" class="mb-1.5 transition-all duration-300 animate-[slideUp_0.2s_ease-out]">
                    <span class="opacity-40 mr-2 text-slate-500">[{{ log.time }}]</span>
                    <span :class="getLogColor(log.level)">[{{ log.level }}] {{ log.message }}</span>
                  </div>
                </div>
              </div>

            </div>
          </div>
        </div>

        <template #fallback>
          <div class="rounded-2xl glass-panel h-[500px] animate-pulse w-full max-w-6xl border-white/10"></div>
        </template>
      </ClientOnly>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, nextTick, watch } from 'vue'

const { t, locale } = useTranslations()

const scenarios = [
  {
    id: 'ai-ops',
    promptText: {
      ko: "OpenAI와 Gemini 모델로 프롬프트를 동시 라우팅해줘. 2초 이상 지연되면 즉시 차단(Fail-Fast)하고 로컬 Ollama 모델로 우회시켜.",
      en: "Route incoming prompt tokens parallelly to OpenAI and Gemini API upstreams. If any provider experiences latency over 2000ms, immediately trigger FAIL_FAST and shift traffic to the local Ollama backup model."
    },
    yamlHtml: `<span class="text-slate-500"># Zefio DSL: Multi-LLM AI Router</span>\n<span class="text-blue-300">steps:</span>\n  <span class="text-slate-500">-</span> <span class="text-blue-300">name:</span> <span class="text-emerald-400">LLM_DISPATCHER</span>\n    <span class="text-blue-300">type:</span> <span class="text-indigo-400">TRY_SCOPE</span>\n    <span class="text-blue-300">on-error:</span> <span class="text-rose-400">FALLBACK</span>\n    <span class="text-blue-300">steps:</span>\n      <span class="text-slate-500">-</span> <span class="text-blue-300">type:</span> <span class="text-indigo-400">SCATTER_GATHER</span>\n        <span class="text-blue-300">config:</span> { <span class="text-amber-200">timeout: 2000, errorPolicy: FAIL_FAST</span> }`,
    nodes: [
      { type: 'INGRESS', name: 'api-gateway' },
      { type: 'SCOPE', name: 'timeout-watcher' },
      { type: 'ROUTER', name: 'llm-dispatcher' },
      { type: 'RECOVERY', name: 'ollama-fallback' }
    ],
    logs: [
      { level: 'INFO', msg: '[GW] Request received: Prompt tokens' },
      { level: 'WARN', msg: '[Scatter] Fan-out to [OpenAI, Gemini]' },
      { level: 'ERROR', msg: '[OpenAI] Latency > 2000ms! FAIL_FAST' },
      { level: 'INFO', msg: '[Fallback] Route shifted to Ollama' }
    ]
  },
  {
    id: 'iot-edge',
    promptText: {
      ko: "커넥티드 카에서 들어오는 실시간 텔레메트리 TCP 스트림을 수집하고, 필터링 후 Kafka 브로커로 분산시켜.",
      en: "Create a high-concurrency TCP server pipeline listening on Port 1883. Parse raw bytecode telemetry, filter anomalies, and fan-out to Kafka."
    },
    yamlHtml: `<span class="text-slate-500"># Zefio DSL: Edge Telemetry Pipeline</span>\n<span class="text-blue-300">steps:</span>\n  <span class="text-slate-500">-</span> <span class="text-blue-300">type:</span> <span class="text-emerald-400">TcpServer</span>\n    <span class="text-blue-300">config:</span> { <span class="text-amber-200">port: 1883</span> }\n  <span class="text-slate-500">-</span> <span class="text-blue-300">type:</span> <span class="text-emerald-400">SpELFilter</span>\n    <span class="text-blue-300">config:</span> { <span class="text-amber-200">expression: "speed < 200"</span> }`,
    nodes: [
      { type: 'INGRESS', name: 'tcp-edge-node' },
      { type: 'INTERCEPTOR', name: 'anomaly-detector' },
      { type: 'UPSTREAM', name: 'kafka-cluster' }
    ],
    logs: [
      { level: 'INFO', msg: '[TCP] Connected 10,000+ edge devices' },
      { level: 'INFO', msg: '[Filter] Validating telemetry bytes' },
      { level: 'WARN', msg: '[Filter] Dropped anomaly: speed > 200' },
      { level: 'INFO', msg: '[Upstream] Kafka partition write success' }
    ]
  },
  {
    id: 'e-commerce',
    promptText: {
      ko: "이커머스 결제 트래픽을 위한 HTTP Ingress 풀을 생성하고 SEDA 버퍼링으로 DB Write 부하를 조절해줘.",
      en: "Build an HTTP ingress pool for checkout requests on 8080. Use a SEDA buffer to throttle DB writes via Redis."
    },
    yamlHtml: `<span class="text-slate-500"># Zefio DSL: Checkout Traffic Buffer</span>\n<span class="text-blue-300">ingress:</span>\n  <span class="text-purple-400">port:</span> <span class="text-amber-200">8080</span>\n<span class="text-blue-300">steps:</span>\n  <span class="text-slate-500">-</span> <span class="text-blue-300">type:</span> <span class="text-emerald-400">SEDA_BUFFER</span>\n    <span class="text-blue-300">config:</span> { <span class="text-amber-200">throttle: 500</span> }`,
    nodes: [
      { type: 'INGRESS', name: 'checkout-ingress' },
      { type: 'INTERCEPTOR', name: 'seda-buffer' },
      { type: 'UPSTREAM', name: 'db-writer' }
    ],
    logs: [
      { level: 'INFO', msg: '[HTTP] Port 8080 ready' },
      { level: 'INFO', msg: '[SEDA] Buffering flash-sale requests' },
      { level: 'WARN', msg: '[Throttle] Redis pressure high' },
      { level: 'INFO', msg: '[DB] Batch write synchronized' }
    ]
  },
  {
    id: 'gaming',
    promptText: {
      ko: "게임 클라이언트 활동 로그를 WebSocket으로 수집하고, 동적 라우팅을 통해 로그를 분석해줘.",
      en: "Ingest player events via WebSockets. Route transactional packets to JDBC and diagnostic logs to Redis."
    },
    yamlHtml: `<span class="text-slate-500"># Zefio DSL: Gaming Live-Ops Splitter</span>\n<span class="text-blue-300">ingress:</span>\n  <span class="text-purple-400">type:</span> <span class="text-emerald-400">WebSocketIngress</span>\n<span class="text-blue-300">steps:</span>\n  <span class="text-slate-500">-</span> <span class="text-blue-300">type:</span> <span class="text-emerald-400">SWITCH</span>\n    <span class="text-blue-300">cases:</span> [ <span class="text-amber-200">TRANSACTIONAL</span> ]`,
    nodes: [
      { type: 'INGRESS', name: 'ws-gateway' },
      { type: 'ROUTER', name: 'event-splitter' },
      { type: 'UPSTREAM', name: 'jdbc-warehouse' },
      { type: 'UPSTREAM', name: 'redis-logger' }
    ],
    logs: [
      { level: 'INFO', msg: '[WS] Player session established' },
      { level: 'INFO', msg: '[Router] Analyzing packet event type' },
      { level: 'INFO', msg: '[JDBC] Monetization log stored' },
      { level: 'INFO', msg: '[Redis] Diagnostic state cached' }
    ]
  }
]

// Simulation logic
const scenarioIndex = ref(0)
const currentScenario = computed(() => scenarios[scenarioIndex.value])
const phase = ref<'typing' | 'yaml' | 'running' | 'done'>('typing')
const displayedPrompt = ref('')
let charIndex = 0
const activeNodeIndex = ref(-1)
const visibleLogs = ref<{time: string, level: string, message: string}[]>([])
const logContainer = ref<HTMLElement | null>(null)
const timers: any[] = []
const clearAllTimers = () => { timers.forEach(clearTimeout); timers.length = 0 }

const playSequence = () => {
  clearAllTimers()
  phase.value = 'typing'
  displayedPrompt.value = ''
  charIndex = 0
  activeNodeIndex.value = -1
  visibleLogs.value = []
  
  const textToType = currentScenario.value.promptText[locale.value as 'ko' | 'en']
  const typeInterval = setInterval(() => {
    displayedPrompt.value = textToType.substring(0, charIndex)
    charIndex++
    if (charIndex > textToType.length) {
      clearInterval(typeInterval)
      phase.value = 'yaml'
      timers.push(setTimeout(() => { startRuntimeSimulation() }, 1000))
    }
  }, 30)
  timers.push(typeInterval)
}

const startRuntimeSimulation = () => {
  phase.value = 'running'
  currentScenario.value.logs.forEach((logItem, idx) => {
    timers.push(setTimeout(() => {
      const now = new Date()
      const timeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`
      visibleLogs.value.push({ time: timeStr, level: logItem.level, message: logItem.msg })
      nextTick(() => { if (logContainer.value) logContainer.value.scrollTop = logContainer.value.scrollHeight })
      if (idx < currentScenario.value.nodes.length) activeNodeIndex.value = idx
    }, idx * 800))
  })
  
  timers.push(setTimeout(() => {
    phase.value = 'done'
    activeNodeIndex.value = -1
    timers.push(setTimeout(() => {
      scenarioIndex.value = (scenarioIndex.value + 1) % scenarios.length
      playSequence()
    }, 1000))
  }, currentScenario.value.logs.length * 800 + 1000))
}

const getNodeClass = (index: number, node: any) => {
  if (activeNodeIndex.value !== index) return 'bg-white/5 text-slate-400 border-white/10'
  return 'bg-blue-500/20 text-blue-300 border-blue-400 shadow-[0_0_15px_rgba(59,130,246,0.3)] scale-105'
}

const getLogColor = (level: string) => level === 'ERROR' ? 'text-rose-400' : level === 'WARN' ? 'text-amber-400' : 'text-slate-300'

onMounted(playSequence)
onUnmounted(clearAllTimers)
</script>