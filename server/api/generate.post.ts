import { defineEventHandler, readBody } from 'h3'
import { useRuntimeConfig, useStorage } from '#imports'
import { GoogleGenerativeAI, SchemaType } from '@google/generative-ai'
import OpenAI from 'openai'
import yaml from 'js-yaml'
import { compileAndFlattenFlows } from '../utils/zefio-compiler' // 💡 Import the shared core compiler engine for instant UI pre-visualization

export default defineEventHandler(async (event) => {
  const body = await readBody(event)
  const config = useRuntimeConfig()
  const storage = useStorage('db') // Access Nitro Storage engine instances

  if (!body || !body.prompt) {
    return { status: 400, message: 'Prompt is required.' }
  }

  try {
    // ====================================================================
    // 1. Dynamically Load Metadata & Globals from Storage for Prompt Injection
    // ====================================================================
    // A. Extract registered Plugin Types directly from Java reflection snapshots
    const masterRegistry: any = (await storage.getItem('zefio:registry:master')) || []
    const ingressTypes = masterRegistry.filter((p: any) => p.type === 'ingress').map((p: any) => p.name).join(', ')
    const upstreamTypes = masterRegistry.filter((p: any) => p.type === 'upstream').map((p: any) => p.name).join(', ')
    const interceptorTypes = masterRegistry.filter((p: any) => p.type === 'interceptor').map((p: any) => p.name).join(', ')
    const faultHandlerTypes = masterRegistry.filter((p: any) => p.type === 'faultHandler').map((p: any) => p.name).join(', ')

    // B. Load Active Global Topology (Exclude dead globalErrors matrix)
    const globalsObj: any = await storage.getItem('zefio:topology:globals')
    const globalsData = globalsObj?.data || {}
    const validTelegrams = Object.keys(globalsData.telegrams || {}).join(', ') || 'No telegrams found. Sync DP first.'
    const validProfiles = Object.keys(globalsData.profiles || {}).join(', ') || 'No profiles found. Sync DP first.'

    // ====================================================================
    // 2. Zefio DSL Grammar & AIOps Rules (Dynamic Injection Applied)
    // ====================================================================
    const systemInstruction = `
You are 'Zefio AI Architect', a veteran enterprise core messaging middleware engineer specializing in SEDA (Staged Event-Driven Architecture) architectures and high-concurrency non-blocking server structures.
Your ONLY goal is to analyze integration requests and output valid configuration objects matching the Zefio Domain Specific Language (DSL), while serving as a senior technical leader.

[0. YOUR BEHAVIORAL PROTOCOL & PERSONA]
- Act as a Supportive, Grounded, Senior Technical Colleague. Before providing the markdown block containing the DSL, you MUST:
  1. Detail the precise architectural rationale behind your proposed SEDA pipeline arrangement.
  2. Point out specific data integrity safeguards, backpressure limits, or boundary edge conditions (The 'Architect's Insight').
- If a request lacks clear boundaries (vague), ask an insightful clarifying architectural question first before drafting configurations.
- Proactively warn the user if they request anti-patterns, such as synchronous threads blocking the core SEDA event loop or tiny channel pools under heavy write operations.

[1. CORE GRAMMAR, SKELETON & ORDERING (MANDATORY)]
- Root Topology: The configuration string structure MUST ALWAYS start with a root "flows" array block.
- Absolute Key Ordering (STRICT CONSTRAINT): Within every flow model instance, keys MUST appear in this exact sequence: 'name', 'label', 'options', 'ingress', 'steps', 'on-error'.
- Component Structure: Every single unit block inside "ingress", "steps", and terminal error paths MUST strictly contain:
  - "name": Short, unique, alphanumeric English slug identifier.
  - "label": Clear, descriptive, human-readable title tracking the business purpose.
  - "type": Resolves to the dynamic framework plugin name.
  - "config": Property field block holding DTO variables mapping directly to the underlying component.

[2. FLOW-LEVEL ERROR HANDLING SPECIFICATION (CRITICAL CHANGE)]
- CRITICAL ARCHITECTURAL UPGRADE: Global centralized error configuration models and "refErrorHandler" reference keys are PERMANENTLY DEPRECATED AND PROHIBITED.
- All terminal error processing paths MUST be defined inline using the fully flattened structure at the flow-root 'on-error' field.
- The 'on-error' field is an array of objects matching the 'ErrorHandlerConfiguration' Java schema layer.
- Each handler object inside the 'on-error' array MUST STRICTLY observe this specific schema:
  - "error-type": The matching exception string token (e.g., "TIMEOUT", "DATABASE_ERROR", or "ANY").
  - "steps": A clean array list of standard 'StepConfiguration' elements (e.g., 'FixedFaultHandler' or custom routing layers).
- CRITICAL CONSTRAINT: Do NOT inject 'type', 'config', or 'refErrorHandler' directly at the 'on-error' root array item level. Doing so crashes Jackson serialization on the Data Plane. You MUST embed execution logic components INSIDE the nested 'steps' array.

[3. BASE TOPOLOGY SKELETON FORMAT]
Your code generation output configuration structure MUST align with this exact JSON schema:
{
  "flows": [
    {
      "name": "your-flow-unique-slug",
      "label": "Human Readable Flow Title",
      "options": {
        "threadPool": { 
          "corePoolSize": 100, 
          "maxPoolSize": 200, 
          "queueCapacity": 1000,
          "autoScaling": { "enabled": true, "threshold": 0.7, "checkInterval": 5, "scaleUpStep": 30, "scaleDownStep": 10 }
        },
        "cpuQueue": { "capacity": 10000 },
        "ioQueue": { "capacity": 5000 }
      },
      "ingress": { "name": "...", "label": "...", "type": "...", "telegram": "...", "profile": "...", "exchangePattern": "...", "config": { ... } },
      "steps": [ 
        { "name": "step-alpha", "label": "Format Parsing Step", "type": "SpELModifierInterceptor", "config": { ... } },
        { "name": "step-beta", "label": "External Endpoint Post", "type": "HttpUpstream", "telegram": "...", "profile": "...", "exchangePattern": "...", "config": { "host": "...", "port": 80 } }
      ],
      "on-error": [
        {
          "error-type": "ANY",
          "steps": [
            {
              "name": "terminal-fault-fallback",
              "label": "Inline Standard Fault Assembly Response",
              "type": "FixedFaultHandler",
              "config": { "errorCodeRules": [], "valueOverrides": [], "messageRule": null }
            }
          ]
        }
      ]
    }
  ]
}

[4. COMPONENT TYPE & PROPERTY CONSTRAINTS]
- If a plugin belongs to an Endpoint Component category (type is 'ingress' OR 'upstream'):
  - IT MUST include: "telegram", "profile", "exchangePattern" fields outside the config block.
  - "exchangePattern" value variants: "FireAndForget" OR "RequestReply".
  - 'telegram' token selection MUST be drawn strictly from this verified list: [${validTelegrams}].
  - 'profile' token selection MUST be drawn strictly from this verified list: [${validProfiles}].
  - 'config' properties here are used exclusively to override static profile allocations (e.g., host, port).
- If a plugin belongs to an Interceptor, Fault Handler, or Core Control Scope category:
  - IT MUST NOT include "telegram", "profile", or "exchangePattern" parameters at the root scope. Injection of these properties causes severe instantiation failure.

[5. RECURSIVE CONTROL FLOW SCOPE SCHEMAS]
Core orchestration boundaries utilize specific composite rules. Never guess or substitute their structural tags:
1. type: "SWITCH" (Route Evaluator Selector)
   - MUST contain a 'cases' array list. Each entry contains a 'condition' (valid SpEL expression, e.g., "#{payload.headers['USER_LEVEL'] == 'GOLD'}") and a 'steps' array.
   - MAY contain a 'defaultSteps' array tracking fallback paths.
2. type: "TRY_SCOPE" (Resilient Boundary Handler)
   - MUST contain a core execution 'steps' array.
   - MAY contain a 'retry' management model block: { "enabled": true, "maxRetries": 2, "delay": 100 }.
   - MAY contain an 'on-error' structural string enum value (STRICT REQUIREMENT: Inside TRY_SCOPE, 'on-error' is a string token e.g., "FALLBACK" | "CONTINUE" | "THROW", NOT an array).
   - MUST contain an alternate 'fallback-steps' execution array if 'on-error' is configured to "FALLBACK".
3. type: "SCATTER_GATHER" (Parallel Fan-Out Aggregator)
   - MUST contain a concurrent execution 'steps' array.
   - MUST provide a 'config' map containing ONLY these exact attributes: "timeout" (integer millis), "aggregationType" ("MAP_MERGE" | "OVERRIDE"), and "errorPolicy" ("FAIL_FAST" | "BEST_EFFORT").

[6. AVAILABLE PLUGIN DICTIONARY VOCABULARY]
You are strictly forbidden from inventing, hallucinating, or assuming component identifiers or variables. You MUST match types exactly against this dictionary context:
- INGRESS Options: ${ingressTypes || 'HttpIngress, WebSocketIngress'}
- UPSTREAM Options: ${upstreamTypes || 'HttpUpstream, TcpUpstream'}
- INTERCEPTOR Options: ${interceptorTypes || 'SpELModifierInterceptor, SpELValidatorInterceptor, LoggingInterceptor'}
- FAULT_HANDLER Options: ${faultHandlerTypes || 'FixedFaultHandler'}
- CORE CONTROL SCOPES: SWITCH, TRY_SCOPE, SCATTER_GATHER
- YOU MUST call 'get_plugin_schema' for EVERY component you use before writing its 'config' block.
- This applies to ALL types: ingress, upstream, interceptor, faultHandler.
- NEVER rely on training data or assumptions for 'config' field names. The schema is the ONLY source of truth.
- Confirmed fields ONLY. If a field is not present in the schema response, it MUST NOT appear in config.

[7. STRICT FINAL OUTPUT ARRANGEMENT]
- Write out the semantic analysis, design methodology, and optimization insights as standard readable text first.
- Wrap your final correct JSON topology payload configuration neatly enclosed inside a markdown block (\`\`\`json ... \`\`\`). Do not add trailing or conversational elements outside the final block code area.
`

    let finalContent = ''

    // ====================================================================
    // 3. Tool Execution Logic (Nitro Database Storage Bridge)
    // ====================================================================
    const executeTool = async (functionName: string, args: any) => {
      console.log(`[Zefio AI] 🔍 Tool Called: ${functionName}`, args)
      
      if (functionName === 'get_plugin_registry') {
        const masterRegistryCache = await storage.getItem('zefio:registry:master')
        return masterRegistryCache || { error: "Registry is empty. Sync DP first." }
      } 
      if (functionName === 'get_plugin_schema') {
        const pluginData: any = await storage.getItem(`zefio:plugin:${args.pluginName}`)
        if (!pluginData) return { error: `Schema not found for: ${args.pluginName}` }

        const schemaFields = Object.keys(pluginData.schema || {})
        
        return {
          pluginName: pluginData.name,
          type: pluginData.type,
          availableConfigFields: schemaFields,
          schema: pluginData.schema
        }
      }
      if (functionName === 'get_global_topology') {
        const globals = await storage.getItem('zefio:topology:globals')
        return globals || { error: "Global configuration context not found. Sync DP first." }
      }
      if (functionName === 'get_flow_list') {
        const flowList = await storage.getItem('zefio:registry:flows')
        return flowList || { error: "Active running flow index is empty." }
      }
      if (functionName === 'get_flow_detail') {
        const flowDetail = await storage.getItem(`zefio:flow:${args.flowName}`)
        return flowDetail || { error: `Flow specification object '${args.flowName}' could not be located.` }
      }

      return { error: "Unknown execution function request handle" }
    }

    // Isolated runtime context function routine handling Local Ollama processing loop
    const runOllamaFallback = async (prompt: string): Promise<string> => {
      console.log(`[Zefio AI] ⚠️ Gemini Agent offline. Activating Local Ollama (${config.ollamaModel})...`)
      
      const ollamaClient = new OpenAI({
        baseURL: config.ollamaBaseUrl,
        apiKey: 'ollama-dummy-auth-token',
      })

      const openaiTools: any[] = [
        { type: "function", function: { name: "get_plugin_registry", description: "Get the master list of all available Zefio plugins.", parameters: { type: "object", properties: {}, required: [] } } },
        { type: "function", function: { name: "get_plugin_schema", description: "Get the detailed DTO schema for a specific plugin. Crucial for understanding valid 'config' properties.", parameters: { type: "object", properties: { pluginName: { type: "string" } }, required: ["pluginName"] } } },
        { type: "function", function: { name: "get_global_topology", description: "Get the global dictionary of profiles, telegrams, and endpoints.", parameters: { type: "object", properties: {}, required: [] } } },
        { type: "function", function: { name: "get_flow_list", description: "Get the list of all running flow names.", parameters: { type: "object", properties: {}, required: [] } } },
        { type: "function", function: { name: "get_flow_detail", description: "Get the detailed structure of a specific flow.", parameters: { type: "object", properties: { flowName: { type: "string" } }, required: ["flowName"] } } }
      ]

      const messages: any[] = [
        { role: "system", content: systemInstruction },
        { role: "user", content: prompt }
      ]

      let response = await ollamaClient.chat.completions.create({
        model: config.ollamaModel,
        messages: messages,
        tools: openaiTools,
      })

      let responseMessage = response.choices[0].message

      // Ollama dynamic function router tool calling resolution loop
      while (responseMessage.tool_calls) {
        messages.push(responseMessage)
        for (const toolCall of responseMessage.tool_calls) {
          const args = JSON.parse(toolCall.function.arguments)
          const apiResponse = await executeTool(toolCall.function.name, args)
          messages.push({
            tool_call_id: toolCall.id,
            role: "tool",
            name: toolCall.function.name,
            content: JSON.stringify(apiResponse),
          })
        }
        response = await ollamaClient.chat.completions.create({
          model: config.ollamaModel,
          messages: messages,
        })
        responseMessage = response.choices[0].message
      }
      return responseMessage.content || ''
    }

    // ====================================================================
    // 4A. Google Gemini Integration (with Functional Tool Declarations)
    // ====================================================================
    if (config.aiProvider === 'gemini') {
      try {
        if (!config.geminiApiKey) throw new Error('GEMINI_API_KEY is missing from environment.')
        console.log(`[Zefio AI] Initiating Google Gemini Agent Core...`)

        const genAI = new GoogleGenerativeAI(config.geminiApiKey)
        
        const geminiTools = [{
          functionDeclarations: [
            {
              name: "get_plugin_registry",
              description: "Get the master list of all available Zefio plugins currently loaded into the platform memory layer."
            },
            {
              name: "get_plugin_schema",
              description: "Get the detailed DTO class schema for a specific plugin token. Critical to analyze valid inner 'config' properties.",
              parameters: {
                type: SchemaType.OBJECT,
                properties: { pluginName: { type: SchemaType.STRING, description: "Exact string handle identification of the plugin" } },
                required: ["pluginName"]
              }
            },
            {
              name: "get_global_topology",
              description: "Get the global map dictionary framework holding definitions for system connection profiles, endpoints, and data telegram layouts."
            },
            {
              name: "get_flow_list",
              description: "Get the absolute array string list tracking all currently running active gateway channel flow identifiers."
            },
            {
              name: "get_flow_detail",
              description: "Get the absolute structured hierarchical YAML/JSON layout specification tree of a single designated flow environment.",
              parameters: {
                type: SchemaType.OBJECT,
                properties: { flowName: { type: SchemaType.STRING, description: "Exact target name identifier of the flow configuration asset" } },
                required: ["flowName"]
              }
            }
          ]
        }]

        const model = genAI.getGenerativeModel({ 
          model: "gemini-2.5-flash", 
          systemInstruction,
          tools: geminiTools
        })

        const chat = model.startChat()
        let result = await chat.sendMessage(body.prompt)
        let functionCalls = result.response.functionCalls()

        // Continuous conversational loop resolution for Gemini tool invocations
        while (functionCalls && functionCalls.length > 0) {
          const call = functionCalls[0]
          const apiResponse = await executeTool(call.name, call.args)

          result = await chat.sendMessage([{
            functionResponse: {
              name: call.name,
              response: { result: apiResponse }
            }
          }])
          functionCalls = result.response.functionCalls()
        }

        finalContent = result.response.text()
        
      } catch (geminiError: any) {
        console.error('[Zefio AI] Gemini API pipeline disrupted, details:', geminiError.message)
        finalContent = await runOllamaFallback(body.prompt) 
      }
    } 

    // ====================================================================
    // 4B. OpenAI (ChatGPT) Core Integration Layer
    // ====================================================================
    else if (config.aiProvider === 'openai') {
      if (!config.openaiApiKey) throw new Error('OPENAI_API_KEY initialization variable is missing.')
      console.log(`[Zefio AI] Initiating OpenAI Enterprise Agent Core...`)
      
      const openai = new OpenAI({ apiKey: config.openaiApiKey })

      const openaiTools: any[] = [
        { type: "function", function: { name: "get_plugin_registry", description: "Get the master list of all available Zefio plugins.", parameters: { type: "object", properties: {}, required: [] } } },
        { type: "function", function: { name: "get_plugin_schema", description: "Get the detailed DTO schema for a specific plugin. Crucial for understanding valid 'config' properties.", parameters: { type: "object", properties: { pluginName: { type: "string" } }, required: ["pluginName"] } } },
        { type: "function", function: { name: "get_global_topology", description: "Get the global dictionary of profiles, telegrams, and endpoints.", parameters: { type: "object", properties: {}, required: [] } } },
        { type: "function", function: { name: "get_flow_list", description: "Get the list of all running flow names.", parameters: { type: "object", properties: {}, required: [] } } },
        { type: "function", function: { name: "get_flow_detail", description: "Get the detailed structure of a specific flow.", parameters: { type: "object", properties: { flowName: { type: "string" } }, required: ["flowName"] } } }
      ]

      const messages: any[] = [
        { role: "system", content: systemInstruction },
        { role: "user", content: body.prompt }
      ]

      let response = await openai.chat.completions.create({
        model: "gpt-4o-mini",
        messages: messages,
        tools: openaiTools,
        tool_choice: "auto"
      })

      let responseMessage = response.choices[0].message

      // Iterative execution loop processing functional tool call chains inside OpenAI context
      while (responseMessage.tool_calls) {
        messages.push(responseMessage)

        for (const toolCall of responseMessage.tool_calls) {
          const args = JSON.parse(toolCall.function.arguments)
          const apiResponse = await executeTool(toolCall.function.name, args)

          messages.push({
            tool_call_id: toolCall.id,
            role: "tool",
            name: toolCall.function.name,
            content: JSON.stringify(apiResponse),
          })
        }

        response = await openai.chat.completions.create({
          model: "gpt-4o-mini",
          messages: messages,
        })
        responseMessage = response.choices[0].message
      }

      finalContent = responseMessage.content || ''
    } 
    else {
      throw new Error(`Unsupported or invalid AI_PROVIDER token declaration: ${config.aiProvider}`)
    }

    // ====================================================================
    // 5. Post-processing (High-Performance JSON-to-YAML Extraction Engine)
    // ====================================================================
    const lowerPrompt = body.prompt.toLowerCase()
    const isPureInquiry = lowerPrompt.includes("알려줘") || lowerPrompt.includes("뭐야") || lowerPrompt.includes("있어")

    if (!isPureInquiry) {
      try {
        let jsonStr = finalContent
        
        // V2 Sniper Boundary Extractor: Locate exact root sequence matching the required 'flows' token array
        const flowsKeywordIndex = jsonStr.indexOf('"flows"')
        
        if (flowsKeywordIndex !== -1) {
          const startIndex = jsonStr.lastIndexOf('{', flowsKeywordIndex)
          const endIndex = jsonStr.lastIndexOf('}')

          if (startIndex !== -1 && endIndex !== -1) {
            jsonStr = jsonStr.substring(startIndex, endIndex + 1)
            const jsonObject = JSON.parse(jsonStr)
            
            // 💡 Critical Fix: Pipe the raw AI-generated payload directly into the compiler engine
            // This flat-injects connections and profiles into inline configurations BEFORE displaying it to the user.
            finalContent = compileAndFlattenFlows(jsonObject.flows, globalsData)
          } else {
            throw new Error("Could not compute valid structural bounds tracking JSON block coordinates.")
          }
        } else {
          throw new Error("Target core collection root 'flows' identifier is completely absent from model text blocks.")
        }

      } catch (parseError: any) {
        console.error('[Zefio AI] JSON string to YAML compiler sequence collapsed. Context logging:\n', finalContent)
        return { 
          status: 500, 
          yaml: finalContent, 
          message: `AI runtime node generated un-parsable boundary configurations: ${parseError.message}` 
        }
      }
    }

    return {
      status: 200,
      yaml: finalContent,
      message: `Processed successfully using specialized ${config.aiProvider} inference.`
    }

  } catch (error: any) {
    console.error('[Zefio AI] Orchestration sequence generation completely failed:', error.message)
    return { status: 500, message: `AI Server Infrastructure Failure: ${error.message}` }
  }
})