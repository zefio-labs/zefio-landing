// composables/useTranslations.ts
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
      footer: { tagline: 'Next-Gen SEDA Integration. Zefio Labs.' }
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
      footer: { tagline: 'Next-Gen SEDA Integration. Zefio Labs.' }
    }
  }

  const t = computed(() => messages[locale.value])
  const setLocale = (newLocale: 'ko' | 'en') => { locale.value = newLocale }

  return { locale, t, setLocale }
}