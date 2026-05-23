// nuxt.config.ts
export default defineNuxtConfig({
  devtools: { enabled: true },

  // Allow external tunneling hosts in dev mode (e.g., Serveo, Ngrok)
  vite: {
    server: {
      allowedHosts: true
    }
  },

  modules: [
    '@nuxtjs/tailwindcss',
    '@pinia/nuxt',
    '@vueuse/nuxt',
    '@nuxt/icon'
  ],

  // Global SEO & Meta tags
  app: {
    head: {
      title: 'Zefio Labs | Innovative Intelligent Gateway for Modern MSA',
      meta: [
        { charset: 'utf-8' },
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
        { name: 'description', content: 'Meet Zefio, the next-generation unified gateway combining high-performance SEDA architecture with an AI Control Plane.' },
        // OpenGraph setup
        { property: 'og:type', content: 'website' },
        { property: 'og:title', content: 'Zefio Labs - Intelligent MSA Gateway' },
        { property: 'og:description', content: 'From HTTP and TCP to Kafka, RabbitMQ, and JDBC. An innovative pipeline designed by AI and executed instantly.' },
        { property: 'og:image', content: '/og-image.png' }
      ],
      link: [
        { rel: 'icon', type: 'image/x-icon', href: '/favicon.ico' }
      ]
    }
  },
  
  nitro: {
    output: {
      // Gather all build outputs into a top-level deploy folder
      dir: '../../deploy/zefio-landing'
    },
    experimental: {
      websocket: true // Core setting for real-time DP communication
    },
    // Server-side storage for caching DP schemas and topologies
    storage: {
      db: {
        driver: 'fs',
        base: './.data/db'
      }
    }
  },

  // Environment variable mapping
  runtimeConfig: {
    // DP Address Mapping (Default fallback to localhost)
    dpApiUrl: process.env.DP_API_URL || 'http://localhost:52001',

    // AI Provider Mapping
    aiProvider: process.env.AI_PROVIDER || 'gemini',
    geminiApiKey: process.env.GEMINI_API_KEY,
    openaiApiKey: process.env.OPENAI_API_KEY,
    
    // Local Fallback (Ollama)
    ollamaBaseUrl: process.env.OLLAMA_BASE_URL || 'http://localhost:11434/v1',
    // ollamaModel: process.env.OLLAMA_MODEL || 'gemma4:e4b',
    ollamaModel: process.env.OLLAMA_MODEL || 'qwen2.5-coder:7b',
    
    public: {}
  },

  // CORS Bypass: Forward requests from /api/dp/** to the actual DP URL
  routeRules: {
    '/api/dp/**': {
      proxy: process.env.DP_API_URL ? `${process.env.DP_API_URL}/**` : 'http://localhost:52001/**'
    }
  },

  css: ['~/assets/css/main.css'],
  compatibilityDate: '2026-04-19'
})