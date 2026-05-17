<template>
  <div class="min-screen bg-[#030712] font-sans flex flex-col text-slate-200 min-h-screen relative overflow-hidden">
    
    <div class="absolute inset-0 z-0 flex justify-between items-center pointer-events-none">
      <div class="w-[500px] h-[500px] bg-blue-600/10 rounded-full blur-[120px] absolute -top-40 -left-20"></div>
      <div class="w-[400px] h-[400px] bg-emerald-600/5 rounded-full blur-[100px] absolute -bottom-20 -right-20"></div>
    </div>

    <header class="bg-[#030712]/70 backdrop-blur-xl border-b border-white/5 px-4 md:px-8 py-4 flex flex-col md:flex-row justify-between items-start md:items-center gap-4 sticky top-0 z-40 relative">
      
      <div class="flex justify-between items-center w-full md:w-auto">
        <div>
          <NuxtLink to="/" class="text-xs font-bold text-slate-500 hover:text-blue-400 transition mb-1 inline-block">
            ← Back to Home
          </NuxtLink>
          <h1 class="text-xl md:text-2xl font-extrabold text-white tracking-tight flex items-center gap-2">
            <div class="p-1 bg-blue-500/10 rounded-lg border border-blue-500/20">
              <Icon name="ph:hexagon-fill" class="w-5 h-5 text-blue-400" />
            </div>
            Zefio <span class="text-transparent bg-clip-text bg-gradient-to-r from-blue-400 to-indigo-400">Console</span>
          </h1>
        </div>

        <div class="flex md:hidden items-center gap-2 px-3 py-1.5 bg-white/5 backdrop-blur-md rounded-full border border-white/10 shadow-lg">
          <span class="relative flex h-2.5 w-2.5">
            <span :class="(wsConnected && store.activeNodes?.length > 0) ? 'bg-emerald-400 animate-ping' : (wsConnected ? 'bg-amber-400 animate-pulse' : 'bg-rose-400')" class="absolute inline-flex h-full w-full rounded-full opacity-75"></span>
            <span :class="(wsConnected && store.activeNodes?.length > 0) ? 'bg-emerald-500' : (wsConnected ? 'bg-amber-500' : 'bg-rose-500')" class="relative inline-flex rounded-full h-2.5 w-2.5"></span>
          </span>
          <span class="text-[10px] font-bold text-slate-300 leading-none">
            {{ !wsConnected ? 'Offline' : (store.activeNodes?.length > 0 ? 'Online' : 'Awaiting') }}
          </span>
        </div>
      </div>

      <div class="flex w-full md:w-auto bg-white/5 p-1 rounded-xl border border-white/10 backdrop-blur-md shadow-inner order-last md:order-none">
        <button 
          @click="activeTab = 'architect'"
          :class="activeTab === 'architect' ? 'bg-white/10 shadow-lg text-blue-400 font-bold border border-white/10' : 'text-slate-400 hover:text-slate-200 font-medium border border-transparent'"
          class="flex-1 md:flex-none justify-center px-4 md:px-6 py-2 text-xs md:text-sm rounded-lg transition-all flex items-center gap-1.5 md:gap-2"
        >
          <Icon name="ph:magic-wand-fill" class="w-4 h-4 drop-shadow-[0_0_5px_rgba(96,165,250,0.5)]" /> 
          <span class="truncate">AI Architect</span>
        </button>
        <button 
          @click="activeTab = 'telemetry'"
          :class="activeTab === 'telemetry' ? 'bg-white/10 shadow-lg text-emerald-400 font-bold border border-white/10' : 'text-slate-400 hover:text-slate-200 font-medium border border-transparent'"
          class="flex-1 md:flex-none justify-center px-4 md:px-6 py-2 text-xs md:text-sm rounded-lg transition-all flex items-center gap-1.5 md:gap-2"
        >
          <Icon name="ph:activity-fill" class="w-4 h-4 drop-shadow-[0_0_5px_rgba(52,211,153,0.5)]" /> 
          <span class="truncate">Live Telemetry</span>
        </button>
      </div>

      <div class="hidden md:flex items-center gap-3 px-4 py-2 bg-white/5 backdrop-blur-md rounded-full border border-white/10 shadow-lg">
        <span class="relative flex h-3 w-3">
          <span :class="(wsConnected && store.activeNodes?.length > 0) ? 'bg-emerald-400 animate-ping' : (wsConnected ? 'bg-amber-400 animate-pulse' : 'bg-rose-400')" class="absolute inline-flex h-full w-full rounded-full opacity-75"></span>
          <span :class="(wsConnected && store.activeNodes?.length > 0) ? 'bg-emerald-500' : (wsConnected ? 'bg-amber-500' : 'bg-rose-500')" class="relative inline-flex rounded-full h-3 w-3"></span>
        </span>
        <div class="flex flex-col">
          <span class="text-xs font-bold text-slate-300 leading-none">
            {{ !wsConnected ? 'CP Offline' : (store.activeNodes?.length > 0 ? 'Cluster Online' : 'Awaiting Nodes') }}
          </span>
          <span class="text-[10px] font-bold mt-0.5" :class="store.activeNodes?.length > 0 ? 'text-emerald-400' : 'text-slate-500'">
            {{ store.activeNodes?.length || 0 }} Nodes Active
          </span>
        </div>
      </div>
    </header>

    <main class="flex-1 p-4 md:p-8 overflow-y-auto relative z-10">
      <DashboardAiArchitectTab 
        v-show="activeTab === 'architect'" 
        @deploy-success="activeTab = 'telemetry'" 
      />
      
      <DashboardTelemetryTab 
        v-show="activeTab === 'telemetry'" 
      />
    </main>

  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useDashboardStore } from '~/stores/dashboard'

// Tab state management (Default: 'architect' for flow design)
const activeTab = ref<'architect' | 'telemetry'>('architect')

// Global state management via WebSocket
const store = useDashboardStore()
const wsConnected = ref(false)
let ws: WebSocket | null = null

const initWebSocket = () => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  ws = new WebSocket(`${protocol}//${window.location.host}/api/ws`)

  ws.onopen = () => { 
    wsConnected.value = true 
    // Notify the server that this is the UI dashboard and start subscription
    ws?.send(JSON.stringify({ type: 'ui_subscribe' }))
  }
  
  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      
      // When receiving cluster telemetry data from the server
      if (data.type === 'telemetry') {
        console.log('[Vue UI] Received Telemetry:', data.payload) 
        
        // Update Pinia store
        store.updateTelemetry(data)
      } 
      else if (data.type === 'sys') {
        console.log('[Zefio CP] System Notice:', data.payload)
      }
    } catch(e) {
      console.error('WS parse error:', e)
    }
  }
  
  ws.onclose = () => {
    wsConnected.value = false
    // Auto-reconnect after 5 seconds
    setTimeout(initWebSocket, 5000)
  }
}

onMounted(() => initWebSocket())
onUnmounted(() => { if (ws) ws.close() })
</script>