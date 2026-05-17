# Zefio Console & Landing (zefio-landing)

Zefio Engine is a next-generation intelligent MSA (Microservices Architecture) gateway based on the SEDA (Staged Event-Driven Architecture) pattern.

The `zefio-landing` project serves as the **Control Plane (CP) Web Interface and Landing Page** for the Zefio ecosystem. Built with Nuxt 3, it provides a seamless dark-glassmorphism UI for real-time cluster monitoring and an AI-powered pipeline designer.

---

## Key Features

* **Real-Time Cluster Telemetry:** Monitor distributed Data Plane (DP) nodes in real-time. View JVM stats, TPS, SEDA stage pools, and connection pools across heterogeneous environments (e.g., Local Desktop + Raspberry Pi) via WebSockets and Redis Pub/Sub.
* **AI-Powered Pipeline Designer (AIOps):** Automatically generate complex integration flows (Scatter-Gather, Fail-Fast, Dynamic Routing) using natural language. Supports Google Gemini, OpenAI, and Local Ollama.
* **Schema-Aware Intelligence:** The AI architect synchronizes with the DP nodes to read actual plugin registries, DTO schemas, and global topologies, ensuring generated YAMLs are 100% compliant with your runtime environment.
* **Zero-Downtime Hot Reload:** Push generated YAML configurations directly from the web console to the Zefio Engine cluster without restarting the servers.
* **Modern UI/UX:** A fully responsive, dark-themed, glassmorphism design optimized for enterprise monitoring and developer experience.

---

## Tech Stack

* **Framework:** Nuxt 3, Vue 3
* **Styling:** TailwindCSS, Nuxt Icon (Phosphor Icons)
* **State Management:** Pinia
* **Backend (Nitro):** Node.js, H3, WebSockets
* **Data & Messaging:** Redis (ioredis)
* **AI Integrations:** `@google/generative-ai`, `openai`

---

## Getting Started

### 1. Prerequisites
Ensure you have the following installed on your system:

* Node.js (v18 or higher)
* pnpm (recommended) or npm/yarn
* A running instance of Redis
* At least one running instance of **Zefio Engine (DP)**

### 2. Installation
Clone the repository and install the dependencies:

```bash
git clone https://github.com/zefio-labs/zefio-landing.git
cd zefio-landing
pnpm install
```

### 3. Environment Configuration
For security, sensitive information like API keys and internal IPs are managed via environment variables.
Copy the example environment file to create your local `.env` file:

```bash
cp .env.example .env
```
Open `.env` and configure it according to your environment. (See the **Configuration** section below for details).

### 4. Run the Development Server
Start the Nuxt development server:

```bash
pnpm dev
```
Navigate to `http://localhost:3000` in your browser to view the landing page and access the Zefio Console.

---

## Configuration (`.env`)

The application uses environment variables to dynamically connect to the Zefio Engine cluster and AI providers. Modify your `.env` file as needed:

* `DP_API_URL`: The HTTP endpoint of your primary Zefio Engine (Data Plane) for schema synchronization. (e.g., `http://localhost:52001` or your custom domain/IP).
* `REDIS_URL`: The Redis connection string used for Control Plane Pub/Sub and telemetry aggregation.
* `AI_PROVIDER`: Choose your AI engine for the Pipeline Designer (`gemini` or `openai`).
* `GEMINI_API_KEY`: Your Google Gemini API key.
* `OPENAI_API_KEY`: Your OpenAI API key.
* `OLLAMA_BASE_URL`: (Optional) The base URL for a local Ollama instance if using local models as a fallback.
* `OLLAMA_MODEL`: (Optional) The specific Ollama model to use (e.g., `gemma4:e4b`).

---

## Project Structure

* `components/`: Vue components for the Landing Page and Dashboard UI.
* `pages/`: Nuxt file-based routing (e.g., `index.vue` for landing, `dashboard.vue` for console).
* `server/api/`: Nitro server endpoints handling AI generation (`generate.post.ts`), deployment (`deploy.post.ts`), and WebSocket connections (`ws.ts`).
* `server/utils/`: Core backend logic for Redis cluster aggregation and DP synchronization.
* `stores/`: Pinia stores for managing real-time WebSocket telemetry data across the UI.
* `assets/css/`: Global stylesheets including custom Tailwind animations and glassmorphism utilities.

---

## License

This project is licensed under the Apache License 2.0. See the `LICENSE` file for details.