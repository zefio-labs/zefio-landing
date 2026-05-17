<template>
  <section class="py-32 relative overflow-hidden bg-[#030712] border-t border-white/5">
    <div class="max-w-7xl mx-auto px-6 relative z-10">
      <div class="grid lg:grid-cols-2 gap-20 items-center">
        
        <div>
          <h2 class="text-4xl md:text-5xl font-black mb-6 leading-tight break-keep text-white">
            {{ t.feat.adapterTitle1 }}<br>
            <span class="text-transparent bg-clip-text bg-gradient-to-r from-emerald-400 to-blue-400">
              {{ t.feat.adapterTitle2 }}
            </span>
          </h2>
          <p class="text-slate-400 mb-10 leading-relaxed break-keep text-lg font-light">
            {{ t.feat.adapterDesc }}
          </p>
          <ul class="space-y-6">
            <li v-for="(item, i) in t.feat.adapterPoints" :key="i" class="flex items-center gap-4 text-slate-200 font-medium">
              <div class="p-2 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 shadow-[0_0_15px_rgba(16,185,129,0.2)]">
                <Icon name="ph:check-bold" class="w-4 h-4" />
              </div>
              {{ item }}
            </li>
          </ul>
        </div>
        
        <div class="relative">
          <div class="absolute inset-0 bg-emerald-500/10 rounded-[2rem] blur-3xl transform scale-110"></div>
          
          <div class="relative glass-panel rounded-[2rem] p-8">
            <div class="text-center mb-10">
              <div class="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-black/40 border border-white/10 text-emerald-400 text-xs font-bold mb-3 backdrop-blur-md">
                <Icon name="ph:plug-bold" class="w-4 h-4" /> Extensible Core
              </div>
            </div>

            <div class="grid grid-cols-2 sm:grid-cols-3 gap-4">
              <div class="flex flex-col items-center p-6 bg-black/40 rounded-2xl border border-white/5 hover:border-blue-400/50 hover:bg-blue-900/20 transition-all group cursor-default">
                <Icon name="ph:globe-hemisphere-west-duotone" class="w-10 h-10 text-blue-400 mb-3 group-hover:-translate-y-1 transition-transform" />
                <span class="text-xs font-bold text-slate-300">HTTP/REST</span>
              </div>
              <div class="flex flex-col items-center p-6 bg-black/40 rounded-2xl border border-white/5 hover:border-orange-400/50 hover:bg-orange-900/20 transition-all group cursor-default">
                <Icon name="ph:queue-duotone" class="w-10 h-10 text-orange-400 mb-3 group-hover:-translate-y-1 transition-transform" />
                <span class="text-xs font-bold text-slate-300">Kafka</span>
              </div>
              <div class="flex flex-col items-center p-6 bg-black/40 rounded-2xl border border-white/5 hover:border-emerald-400/50 hover:bg-emerald-900/20 transition-all group cursor-default">
                <Icon name="ph:plugs-connected-duotone" class="w-10 h-10 text-emerald-400 mb-3 group-hover:-translate-y-1 transition-transform" />
                <span class="text-xs font-bold text-slate-300">TCP Socket</span>
              </div>
              <div class="flex flex-col items-center p-6 bg-black/40 rounded-2xl border border-white/5 hover:border-rose-400/50 hover:bg-rose-900/20 transition-all group cursor-default">
                <Icon name="ph:database-duotone" class="w-10 h-10 text-rose-400 mb-3 group-hover:-translate-y-1 transition-transform" />
                <span class="text-xs font-bold text-slate-300">JDBC</span>
              </div>
              <div class="flex flex-col items-center p-6 bg-black/40 rounded-2xl border border-white/5 hover:border-red-400/50 hover:bg-red-900/20 transition-all group cursor-default">
                <Icon name="ph:stack-duotone" class="w-10 h-10 text-red-400 mb-3 group-hover:-translate-y-1 transition-transform" />
                <span class="text-xs font-bold text-slate-300">Redis</span>
              </div>
              <div class="flex flex-col items-center p-6 bg-black/40 rounded-2xl border border-white/10 border-dashed hover:border-white/40 transition-all group cursor-default justify-center">
                <Icon name="ph:plus-circle-duotone" class="w-8 h-8 text-slate-500 mb-2 group-hover:text-white transition-colors" />
                <span class="text-[10px] font-bold text-slate-500 group-hover:text-white uppercase tracking-widest">Custom SDK</span>
              </div>
            </div>
          </div>
        </div>
        
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
const { t } = useTranslations()
</script>