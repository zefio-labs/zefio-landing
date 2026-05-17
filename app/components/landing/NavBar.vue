<template>
  <nav class="bg-[#030712]/70 backdrop-blur-xl border-b border-white/5 fixed w-full z-50 transition-all duration-300">
    <div class="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
      
      <div class="flex items-center gap-3 cursor-pointer" @click="$router.push('/')">
        <div class="p-1.5 bg-blue-500/10 rounded-lg border border-blue-500/20">
          <Icon name="ph:hexagon-fill" class="w-6 h-6 text-blue-400" />
        </div>
        <span class="text-xl font-black tracking-tight text-white">
          Zefio<span class="text-slate-500 font-medium">Labs</span>
        </span>
      </div>
      
      <div class="flex gap-6 items-center">
        <div class="flex bg-white/5 p-1 rounded-full border border-white/10">
          <button @click="setLocale('ko')" :class="locale === 'ko' ? 'bg-white/10 shadow-sm text-white' : 'text-slate-500 hover:text-slate-300'" class="px-3 py-1 text-xs font-bold rounded-full transition-all">KO</button>
          <button @click="setLocale('en')" :class="locale === 'en' ? 'bg-white/10 shadow-sm text-white' : 'text-slate-500 hover:text-slate-300'" class="px-3 py-1 text-xs font-bold rounded-full transition-all">EN</button>
        </div>
        
        <a href="https://github.com/zefio-labs" target="_blank" class="hidden sm:flex items-center gap-2 text-sm font-medium text-slate-400 hover:text-white transition">
          <Icon name="ph:github-logo-fill" class="w-5 h-5" /> {{ t.nav.opensource }}
        </a>
        <NuxtLink to="/dashboard" class="relative group">
          <div class="absolute -inset-0.5 bg-gradient-to-r from-blue-500 to-emerald-500 rounded-full blur opacity-50 group-hover:opacity-100 transition duration-300"></div>
          <button class="relative text-sm font-bold bg-[#030712] border border-white/10 text-white px-6 py-2 rounded-full transition flex items-center gap-2">
            {{ t.nav.console }} <Icon name="ph:arrow-right-bold" class="w-4 h-4" />
          </button>
        </NuxtLink>
      </div>
    </div>
  </nav>
</template>

<script setup lang="ts">
const { t, locale, setLocale } = useTranslations()
</script>