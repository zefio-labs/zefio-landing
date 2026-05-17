<!-- components/landing/PricingSection.vue -->
<template>
  <section id="editions" class="py-32 px-6 bg-slate-50 relative">
    <div class="max-w-6xl mx-auto">
      <div class="text-center mb-20">
        <h2 class="text-4xl md:text-5xl font-black mb-6 text-slate-900">{{ t.editions.title }}</h2>
        <p class="text-slate-500 font-medium text-lg max-w-2xl mx-auto break-keep">{{ t.editions.subtitle }}</p>
      </div>
      
      <div class="grid md:grid-cols-2 gap-8 lg:gap-12">
        
        <!-- Standard Edition -->
        <div class="bg-white rounded-[2rem] p-10 border border-slate-200 shadow-sm flex flex-col transition-all hover:shadow-xl hover:-translate-y-1">
          <div class="mb-8">
            <h3 class="text-2xl font-black text-slate-900 mb-3">Standard</h3>
            <p class="text-slate-500 text-sm h-10 break-keep">{{ t.editions.std.desc }}</p>
          </div>
          <div class="mb-10 flex items-baseline border-b border-slate-100 pb-8">
            <span class="text-6xl font-black text-slate-900">{{ t.editions.std.price }}</span>
            <span class="text-slate-500 font-medium ml-3">{{ t.editions.std.period }}</span>
          </div>
          <ul class="space-y-5 mb-10 flex-1">
            <li v-for="(feat, idx) in t.editions.std.features" :key="idx" class="flex items-start gap-3 text-sm text-slate-700 font-medium">
              <Icon name="ph:check-circle-fill" class="w-5 h-5 text-emerald-500 shrink-0 mt-0.5" /> 
              <span class="break-keep">{{ feat }}</span>
            </li>
          </ul>
          <a href="https://github.com/zefio-labs/zefio-engine" target="_blank" class="block w-full py-4 bg-slate-100 text-slate-800 text-center font-bold rounded-xl hover:bg-slate-200 transition">
            {{ t.editions.std.btn }}
          </a>
        </div>

        <!-- Advanced Edition -->
        <div class="bg-slate-900 rounded-[2rem] p-10 border border-slate-800 shadow-2xl shadow-blue-900/20 relative flex flex-col text-white transform md:scale-105 z-10">
          <div class="absolute top-0 right-0 bg-gradient-to-r from-blue-500 to-indigo-500 text-white text-[10px] font-black px-6 py-2.5 rounded-bl-2xl rounded-tr-[2rem] uppercase tracking-widest shadow-lg">
            {{ t.editions.adv.badge }}
          </div>
          <div class="mb-8">
            <h3 class="text-2xl font-black text-white mb-3">Advanced</h3>
            <p class="text-slate-400 text-sm h-10 break-keep">{{ t.editions.adv.desc }}</p>
          </div>
          <div class="mb-10 flex items-baseline border-b border-slate-700/50 pb-8">
            <span class="text-6xl font-black">{{ t.editions.adv.price }}</span>
            <span class="text-slate-400 font-medium ml-3">{{ t.editions.adv.period }}</span>
          </div>
          <ul class="space-y-5 mb-10 flex-1">
            <li v-for="(feat, idx) in t.editions.adv.features" :key="idx" class="flex items-start gap-3 text-sm text-slate-200 font-medium">
              <Icon name="ph:lightning-fill" class="w-5 h-5 text-blue-400 shrink-0 mt-0.5" /> 
              <span class="break-keep">{{ feat }}</span>
            </li>
          </ul>
          <button class="block w-full py-4 bg-blue-500 text-white text-center font-bold rounded-xl hover:bg-blue-600 transition shadow-lg shadow-blue-500/40 hover:-translate-y-0.5">
            {{ t.editions.adv.btn }}
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
const { t } = useTranslations()
</script>