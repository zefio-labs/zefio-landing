<template>
  <div class="max-w-7xl mx-auto h-full flex flex-col animate-[fadeIn_0.3s_ease-out]">
    
    <div class="mb-6 flex justify-between items-end">
      <div>
        <h2 class="text-2xl font-black text-white text-glow">{{ t.telemetry.title }}</h2>
        <p class="text-sm text-slate-400 mt-1">{{ t.telemetry.subtitle }}</p>
      </div>
      <div class="flex items-center gap-2 bg-white/5 backdrop-blur-md px-4 py-2 rounded-xl shadow-lg border border-white/10">
        <span class="flex h-2.5 w-2.5 rounded-full" :class="store.activeNodes?.length > 0 ? 'bg-emerald-400 animate-pulse' : 'bg-slate-500'"></span>
        <span class="text-xs font-bold text-slate-300">{{ t.telemetry.enginesActive }}: 
          <span class="text-emerald-400">{{ store.activeNodes?.length || 0 }}</span>
        </span>
      </div>
    </div>

    <div class="grid grid-cols-1 xl:grid-cols-3 gap-6 mb-6">
      <div class="xl:col-span-2 space-y-6">
        
        <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div class="glass-panel p-6 rounded-2xl">
            <h2 class="text-xs font-bold text-slate-400 uppercase tracking-widest mb-4 flex items-center gap-2">
              <Icon name="ph:cpu-bold" class="w-5 h-5 text-blue-400" /> Infrastructure
            </h2>
            <div class="space-y-4">
              <div>
                <div class="flex justify-between text-sm mb-1">
                  <span class="font-medium text-slate-300">{{ t.telemetry.totalHeap }}</span>
                  <span class="font-bold text-white">{{ store.jvmStats?.usedHeapMb || 0 }} / {{ store.jvmStats?.maxHeapMb || 0 }} MB</span>
                </div>
                <div class="w-full bg-black/50 rounded-full h-2 border border-white/5">
                  <div class="bg-blue-500 h-2 rounded-full transition-all duration-700" :style="{ width: `${((store.jvmStats?.usedHeapMb || 0) / ((store.jvmStats?.maxHeapMb) || 1)) * 100}%` }"></div>
                </div>
              </div>
              <div class="grid grid-cols-2 gap-4 pt-4 border-t border-white/10">
                <div>
                  <p class="text-[10px] text-slate-400 font-bold uppercase">{{ t.telemetry.avgCpu }}</p>
                  <p class="text-2xl font-black text-white">{{ store.jvmStats?.cpuUsagePercent || 0 }}%</p>
                </div>
                <div>
                  <p class="text-[10px] text-slate-400 font-bold uppercase">{{ t.telemetry.status }}</p>
                  <p class="text-lg font-black mt-1" :class="store.activeNodes?.length > 0 ? 'text-emerald-400' : 'text-slate-500'">
                    {{ store.activeNodes?.length > 0 ? 'HEALTHY' : 'WAITING' }}
                  </p>
                </div>
              </div>
            </div>
          </div>

          <div class="glass-panel p-6 rounded-2xl">
            <h2 class="text-xs font-bold text-slate-400 uppercase tracking-widest mb-4 flex items-center gap-2">
              <Icon name="ph:chart-line-up-bold" class="w-5 h-5 text-emerald-400" /> Throughput
            </h2>
            <div class="flex items-end justify-between h-24">
              <div>
                <p class="text-sm font-semibold text-slate-400 mb-1">{{ t.telemetry.totalTps }}</p>
                <p class="text-6xl font-black text-white leading-none tracking-tighter">{{ store.globalMetrics?.tps || 0 }}</p>
              </div>
              <div class="text-right">
                <p class="text-xs font-bold text-rose-400 bg-rose-500/10 px-3 py-1.5 rounded-lg mb-2 border border-rose-500/20 backdrop-blur-sm">
                  {{ t.telemetry.errors }}: {{ store.globalMetrics?.totalFailures || 0 }}
                </p>
              </div>
            </div>
          </div>
        </div>

        <div class="glass-panel p-6 rounded-2xl">
          <h2 class="text-xs font-bold text-slate-400 uppercase tracking-widest mb-4 flex items-center gap-2">
            <Icon name="ph:stack-bold" class="w-5 h-5 text-indigo-400" /> {{ t.telemetry.sedaPools }}
          </h2>
          <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div v-for="pool in store.sedaPools" :key="pool.plugin" class="p-4 bg-black/40 rounded-xl border border-white/5">
              <p class="text-[10px] font-bold text-slate-400 uppercase truncate">{{ pool.plugin }}</p>
              <div class="flex items-end gap-1 mt-2">
                <span class="text-xl font-black text-white leading-none">{{ pool.active }}</span>
                <span class="text-xs text-slate-500 mb-0.5">/ {{ pool.max }}</span>
              </div>
            </div>
            <div v-if="!store.sedaPools || store.sedaPools.length === 0" class="col-span-full py-8 text-center text-sm text-slate-500 italic">
              {{ t.telemetry.noData }}
            </div>
          </div>
        </div>
      </div>

      <div class="glass-panel p-6 rounded-2xl">
        <h2 class="text-xs font-bold text-slate-400 uppercase tracking-widest mb-4 flex items-center gap-2">
          <Icon name="ph:plugs-connected-bold" class="w-5 h-5 text-emerald-400" /> {{ t.telemetry.connections }}
        </h2>
        <div class="space-y-4 max-h-[500px] overflow-y-auto scrollbar-hide pr-2">
          <div v-for="pool in store.connectionPools" :key="pool.name + pool.flow" class="p-4 rounded-xl border border-white/5 bg-black/40">
            <div class="flex justify-between items-start mb-3">
              <div>
                <p class="text-[10px] font-bold text-slate-400 uppercase">{{ pool.flow }}</p>
                <h3 class="text-sm font-black text-slate-200 truncate">{{ pool.name }}</h3>
              </div>
            </div>
            <div class="w-full bg-black/60 h-1.5 rounded-full overflow-hidden border border-white/5">
              <div class="bg-emerald-400 h-full" :style="{ width: (pool.active / (pool.max || 1) * 100) + '%' }"></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useDashboardStore } from '~/stores/dashboard'
const { t } = useTranslations()
const store = useDashboardStore()
</script>

<style scoped>
.scrollbar-hide::-webkit-scrollbar { display: none; }
.scrollbar-hide { -ms-overflow-style: none; scrollbar-width: none; }
</style>