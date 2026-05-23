<template>
  <div class="max-w-5xl mx-auto h-full flex flex-col animate-[fadeIn_0.3s_ease-out]">
    <div class="mb-6">
      <h2 class="text-2xl font-black text-white text-glow">Pipeline Designer</h2>
      <p class="text-sm text-slate-400 mt-1">Design and deploy Zefio integration flows using Schema-Aware AI across various industries.</p>
    </div>

    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
      <div 
        v-for="preset in industryPresets" 
        :key="preset.id"
        @click="selectPreset(preset.prompt)"
        class="p-4 bg-black/40 border border-white/5 rounded-xl cursor-pointer hover:border-blue-500/50 hover:bg-blue-500/5 transition-all duration-300 group relative overflow-hidden shadow-sm"
      >
        <div class="absolute -right-4 -bottom-4 opacity-10 group-hover:opacity-20 transition-opacity">
          <Icon :name="preset.icon" class="w-20 h-20 text-white" />
        </div>
        <div class="flex items-center gap-2 mb-2">
          <Icon :name="preset.icon" class="w-5 h-5 text-blue-400" />
          <span class="text-xs font-bold text-slate-300 uppercase tracking-wider">{{ preset.industry }}</span>
        </div>
        <h4 class="text-sm font-bold text-white mb-1 group-hover:text-blue-300 transition-colors">{{ preset.title }}</h4>
        <p class="text-[11px] text-slate-500 leading-snug break-keep">{{ preset.desc }}</p>
      </div>
    </div>

    <div class="relative glass-panel p-8 rounded-2xl flex flex-col text-white flex-1 min-h-[600px] overflow-hidden">
      <div class="absolute -top-32 -right-32 w-96 h-96 bg-blue-600/20 rounded-full blur-[100px] pointer-events-none"></div>
      
      <div class="flex items-center justify-between mb-6 relative z-10">
        <h2 class="text-sm font-bold text-blue-400 uppercase tracking-widest flex items-center gap-2">
          <Icon name="ph:robot-fill" class="w-5 h-5 drop-shadow-[0_0_8px_rgba(96,165,250,0.8)]" /> Zefio AIOps Assistant
        </h2>
        <span class="text-xs bg-emerald-500/10 text-emerald-400 px-3 py-1 rounded-full border border-emerald-500/20 flex items-center gap-2 backdrop-blur-md">
          <span class="w-2 h-2 rounded-full bg-emerald-400 animate-pulse shadow-[0_0_8px_rgba(52,211,153,0.8)]"></span> Context Synced
        </span>
      </div>
      
      <textarea 
        v-model="aiPrompt" 
        placeholder="Select a preset template above or enter your custom requirements here... (e.g., Route incoming TCP traffic to Kafka)" 
        class="w-full relative z-10 bg-black/40 border border-white/10 text-slate-200 placeholder-slate-600 p-5 rounded-xl text-sm focus:ring-2 focus:ring-blue-500/50 focus:border-blue-500/50 outline-none mb-6 min-h-[120px] resize-none shadow-inner transition-all duration-300"
      ></textarea>
      
      <button 
        @click="generateYaml" 
        :disabled="isGenerating || !aiPrompt"
        class="w-full relative z-10 bg-gradient-to-r from-blue-600 to-indigo-600 text-white font-bold py-4 rounded-xl hover:from-blue-500 hover:to-indigo-500 transition-all duration-300 disabled:opacity-50 disabled:grayscale flex justify-center items-center gap-2 shadow-[0_0_20px_rgba(59,130,246,0.3)] hover:shadow-[0_0_25px_rgba(59,130,246,0.5)] border border-white/10"
      >
        <Icon v-if="isGenerating" name="ph:spinner-gap-bold" class="w-5 h-5 animate-spin" />
        <Icon v-else name="ph:magic-wand-bold" class="w-5 h-5" />
        {{ isGenerating ? 'Consulting AI Architect...' : 'Generate Pipeline YAML' }}
      </button>

      <div v-if="generatedYaml" class="mt-8 flex-1 flex flex-col min-h-0 animate-[slideUp_0.3s_ease-out] relative z-10">
        <div class="flex justify-between items-center mb-3">
          <span class="text-xs font-bold text-slate-400 uppercase tracking-widest flex items-center gap-2">
            <Icon name="ph:code-block-bold" class="w-4 h-4 text-emerald-400" /> Generated Configuration
          </span>
          <div class="flex gap-2">
            <button @click="copyYaml" class="text-xs bg-white/5 hover:bg-white/10 px-4 py-2 rounded-lg text-slate-300 transition font-bold border border-white/10 flex items-center gap-2 backdrop-blur-md">
              <Icon name="ph:copy-bold" class="w-4 h-4" /> COPY
            </button>
            <button @click="deployYaml" :disabled="isDeploying" class="text-xs bg-emerald-600/80 hover:bg-emerald-500 disabled:bg-slate-700 disabled:border-slate-600 px-4 py-2 rounded-lg text-white transition font-bold flex items-center gap-2 shadow-[0_0_15px_rgba(16,185,129,0.3)] border border-emerald-500/50 backdrop-blur-md">
              <Icon v-if="isDeploying" name="ph:spinner-gap-bold" class="w-4 h-4 animate-spin" />
              <Icon v-else name="ph:rocket-launch-bold" class="w-4 h-4" />
              {{ isDeploying ? 'DEPLOYING...' : 'PUSH TO ENGINE' }}
            </button>
          </div>
        </div>
        <div class="relative flex-1 overflow-hidden rounded-xl border border-white/10 bg-[#030712] shadow-inner">
          <pre class="p-6 text-sm font-mono text-emerald-300 overflow-auto h-full scrollbar-hide whitespace-pre-wrap leading-relaxed">{{ generatedYaml }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits(['deploy-success'])

const aiPrompt = ref('')
const isGenerating = ref(false)
const generatedYaml = ref('')
const isDeploying = ref(false)

// 🚀 Industry-diverse pre-defined template options
const industryPresets = [
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
    prompt: 'Create a high-concurrency TCP server pipeline listening on Port 1883. Parse raw bytecode telemetry streams, apply an intelligent deduplication filter stage, and immediately fan-out to a Kafka cluster broker.'
  },
  {
    id: 'e-commerce',
    industry: 'E-Commerce',
    icon: 'ph:shopping-cart-duotone',
    title: 'Spike Traffic Buffer',
    desc: 'Asynchronous backpressure queue controller for mass checkout flash sales.',
    prompt: 'Build an HTTP ingress pool for order checkout requests on Port 8080. Integrate a SEDA staging memory buffer to throttle downstream database writes via Redis, enforcing an asynchronous backpressure policy.'
  },
  {
    id: 'gaming',
    industry: 'Game Live-Ops',
    icon: 'ph:game-controller-duotone',
    title: 'Live Telemetry Splitter',
    desc: 'Dynamic real-time routing engine for tracking user state and monetization logs.',
    prompt: 'Ingest global player activity events via WebSockets. Dynamically evaluate payload contents using SpEL expressions: route transactional event packets to JDBC data warehouses and diagnostic logs to a Redis pool.'
  }
]

const selectPreset = (prompt: string) => {
  aiPrompt.value = prompt
}

const generateYaml = async () => {
  if (!aiPrompt.value) return
  isGenerating.value = true
  generatedYaml.value = ''

  try {
    const res: any = await $fetch('/api/generate', {
      method: 'POST',
      body: { prompt: aiPrompt.value }
    })

    if (res.status === 200 && res.yaml !== undefined) {
      generatedYaml.value = res.yaml
    } else {
      alert(`[AI Error] ${res.message}`)
    }
  } catch (e: any) {
    alert('[Connection Error] AI Service is temporarily unavailable.')
  } finally {
    isGenerating.value = false
  }
}

const copyYaml = () => {
  if (!generatedYaml.value) return
  navigator.clipboard.writeText(generatedYaml.value)
    .then(() => alert('YAML configuration copied to clipboard!'))
    .catch(err => console.error('Failed to copy: ', err))
}

const deployYaml = async () => {
  if (!generatedYaml.value) return
  if (!confirm('CAUTION: Deploying this YAML will perform a Zero-Downtime Hot-Reload on the engine. Proceed?')) return

  isDeploying.value = true
  try {
    const res: any = await $fetch('/api/deploy', {
      method: 'POST',
      body: { yaml: generatedYaml.value }
    })
    alert('Deployment Result: ' + res.message)
    
    emit('deploy-success')
    
  } catch (e) {
    alert('Deployment failed. Ensure engine (DP) is reachable on Port 52001.')
  } finally {
    isDeploying.value = false
  }
}
</script>

<style scoped>
.scrollbar-hide::-webkit-scrollbar { display: none; }
.scrollbar-hide { -ms-overflow-style: none; scrollbar-width: none; }

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes slideUp {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>