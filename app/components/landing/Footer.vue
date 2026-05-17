<template>
  <footer class="bg-[#030712] border-t border-white/5 py-12 text-center text-slate-500 text-sm">
    <div class="max-w-7xl mx-auto px-6">
      <div class="flex justify-center gap-6 mb-6 text-slate-600">
        <Icon name="ph:github-logo-fill" class="w-6 h-6 hover:text-white cursor-pointer transition-colors" />
      </div>
      <p class="font-bold text-slate-400 mb-2">&copy; 2026 Zefio Labs. All rights reserved.</p>
      <p class="text-slate-600">{{ t.footer.tagline }}</p>
    </div>
  </footer>
</template>

<script setup lang="ts">
const { t } = useTranslations()
</script>