<template>
  <div class="min-h-screen bg-[#030712] text-slate-200 selection:bg-blue-500/30 selection:text-blue-200 overflow-x-hidden">
    
    <LandingNavBar />

    <LandingHeroSection />

    <LandingFeatureSection />

    <LandingFooter />
    
  </div>
</template>

<script setup lang="ts">
// 데이터(다국어 포함)는 각 컴포넌트 내부에서 useTranslations()로 처리
</script>