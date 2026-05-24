// server/api/generate.post.ts
import { defineEventHandler, readBody } from 'h3'
import { useRuntimeConfig, useStorage } from '#imports'
import { GoogleGenerativeAI, SchemaType } from '@google/generative-ai'
import OpenAI from 'openai'
import yaml from 'js-yaml'

export default defineEventHandler(async (event) => {
  const body = await readBody(event)
  const config = useRuntimeConfig()
  const storage = useStorage('db') // Access Nitro Storage

  if (!body || !body.prompt) {
    return { status: 400, message: 'Prompt is required.' }
  }

  try {
    // ====================================================================
    // 1. Dynamically Load Metadata & Globals from Storage for Prompt Injection
    // ====================================================================
    // A. Plugin Types
    const masterRegistry: any = (await storage.getItem('zefio:registry:master')) || []
    const ingressTypes = masterRegistry.filter((p: any) => p.type === 'ingress').map((p: any) => p.name).join(', ')
    const upstreamTypes = masterRegistry.filter((p: any) => p.type === 'upstream').map((p: any) => p.name).join(', ')
    const interceptorTypes = masterRegistry.filter((p: any) => p.type === 'interceptor').map((p: any) => p.name).join(', ')

    // B. Global Topology (Telegrams & Profiles)
    const globalsObj: any = await storage.getItem('zefio:topology:globals')
    const globalsData = globalsObj?.data || {}
    const validTelegrams = Object.keys(globalsData.telegrams || {}).join(', ') || 'No telegrams found. Sync DP first.'
    const validProfiles = Object.keys(globalsData.profiles || {}).join(', ') || 'No profiles found. Sync DP first.'

    // ====================================================================
    // 2. Zefio DSL Grammar & AIOps Rules (Dynamic Injection Applied)
    // ====================================================================
    const systemInstruction = `
You are 'Zefio AI Architect', an expert configuring the Zefio SEDA Engine.
Your ONLY goal is to generate valid JSON configurations that strictly follow the Zefio Domain Specific Language (DSL).

[1. CORE GRAMMAR, SKELETON & ORDERING (CRITICAL)]
- Root Structure: The configuration MUST ALWAYS start with a "flows" array.
- Key Ordering (STRICT): Inside a flow object, you MUST strictly output keys in this exact order: 'name', 'label', 'options', 'ingress', 'steps', 'on-error'.
- Naming Convention (MANDATORY): 
  - Every component in "ingress" and "steps" MUST have:
    - "name": Short, unique, English alphanumeric key.
    - "label": Short, human-readable description.
  - You MUST ALWAYS use 'type' (for the module name) and 'config' (for properties).
- Base Skeleton format MUST EXACTLY match this nested structure:
{
  "flows": [
    {
      "name": "your-flow-name",
      "label": "Flow Description",
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
      "steps": [ { "name": "...", "label": "...", "type": "...", "config": { ... } } ],
      "on-error": [ { "error-type": "ANY", "refErrorHandler": "fixederror" } ]
    }
  ]
}

[2. COMPONENT TYPE & FIELD CONSTRAINTS (CRITICAL)]
- Before generating any component, you MUST identify the plugin's 'type'.
- IF type IS 'ingress' OR 'upstream':
  - MUST include: "telegram", "profile", "exchangePattern".
  - "exchangePattern" MUST be: "FireAndForget" OR "RequestReply".
  - CRITICAL: 'telegram' MUST be chosen strictly from this list: [${validTelegrams}]. DO NOT invent or guess telegram names.
  - CRITICAL: 'profile' MUST be chosen strictly from this list: [${validProfiles}]. DO NOT invent or guess profile names.
- IF type IS 'interceptor':
  - MUST NOT include: "telegram", "profile", "exchangePattern". (Including them causes runtime errors).

[3. SEDA INFRASTRUCTURE SIZING RULES (CRITICAL)]
You MUST output the 'options' object EXACTLY as a nested JSON object. Do NOT use dot-notation.
CRITICAL: 'autoScaling' MUST be placed INSIDE the 'threadPool' object.
1. Mass Traffic / High-Throughput Sync Profile:
   - threadPool: corePoolSize 200~1000, maxPoolSize 400~2000, queueCapacity 0~100, autoScaling: { "enabled": true, "threshold": 0.7, "checkInterval": 5, "scaleUpStep": 30, "scaleDownStep": 10 }
   - cpuQueue: capacity 50000
   - ioQueue: capacity 20000
2. Spike Buffer / Asynchronous Ingress Profile:
   - threadPool: corePoolSize 1000, maxPoolSize 2000, queueCapacity 2000, autoScaling: { "enabled": true, "threshold": 0.5, "checkInterval": 5, "scaleUpStep": 2, "scaleDownStep": 1 }
   - cpuQueue: capacity 10000
   - ioQueue: capacity 5000
3. Standard / Sub-Flow Profile:
   - threadPool: corePoolSize 10~50, maxPoolSize 20~100, queueCapacity 50~100
   - cpuQueue: capacity 5000
   - ioQueue: capacity 2000

[4. CORE SCOPES & RECURSIVE CONTROL FLOW RULES (CRITICAL)]
These types are engine core scopes (Composite Pattern), NOT plugins. Follow their strict schemas:

1. type: "SWITCH" (Conditional Route Selector)
   - MUST contain 'cases' array. Each item MUST have 'condition' (SpEL string, e.g., "#{payload.headers['X'] == 'Y'}") and 'steps' (array).
   - MAY contain 'defaultSteps' (array of steps).

2. type: "TRY_SCOPE" (Resilient Scope Handler)
   - MUST contain 'steps' array (the main logic).
   - MAY contain 'retry' object: { "enabled": true/false, "maxRetries": number, "delay": number }.
   - MAY contain 'on-error' property. CRITICAL: Inside TRY_SCOPE, 'on-error' MUST be a STRING ENUM ("FALLBACK" | "CONTINUE" | "STOP" | "THROW"), NOT an array.
   - MAY contain 'fallback-steps' (array) if on-error is "FALLBACK".

3. type: "SCATTER_GATHER" (Parallel Router)
   - MUST contain 'steps' array (each item is executed in parallel).
   - MUST contain 'config' object with ONLY these properties:
     - "timeout": number (e.g., 3000)
     - "aggregationType": STRING ENUM ("MAP_MERGE" OR "OVERRIDE")
     - "errorPolicy": STRING ENUM ("FAIL_FAST" OR "BEST_EFFORT")

[5. AVAILABLE PLUGIN VOCABULARY & DTO SCHEMA (CRITICAL)]
You MUST ONLY use the exact names listed below for the 'type' field. Do NOT invent or guess plugin names.
- INGRESS types: ${ingressTypes || 'HttpIngress, WebSocketIngress'}
- UPSTREAM types: ${upstreamTypes || 'HttpUpstream, TcpUpstream'}
- INTERCEPTOR types: ${interceptorTypes || 'SpELModifierInterceptor, LoggingInterceptor'}
- CORE SCOPES: SWITCH, TRY_SCOPE, SCATTER_GATHER

CRITICAL HALLUCINATION WARNING: You are strictly forbidden from guessing fields inside the 'config' object. 
For example, 'DynamicLocalUpstream' uses 'targetFlowExpression' and 'timeout', NOT 'endpoint' or 'host'.
If you are generating a 'config' block for a plugin, you MUST call the 'get_plugin_schema' tool using the exact plugin name to get the correct DTO schema fields before writing the JSON.

[6. UPSTREAM ROUTING & TOOL USAGE (CRITICAL)]
- Hybrid Upstream Routing:
  1) 1:1 Dedicated Pipeline: For direct connections (e.g., 'TcpUpstream'), you MUST inline 'host' and 'port' inside 'config'.
  2) N:M Dynamic Routing: For dynamic MSA routing, you MUST use 'SWITCH' combined with 'DynamicLocalUpstream'.
- STOP AND SEARCH: If modifying existing flows, ALWAYS use 'get_flow_list' and 'get_flow_detail' first.

[7. STRICT OUTPUT FORMAT]
- Output ONLY a raw JSON object.
- Do NOT wrap the output in markdown code blocks like \`\`\`json.
- Do NOT add any conversational text before or after the JSON.
`
    let finalContent = ''

    // ====================================================================
    // 3. Tool Execution Logic (Storage Access)
    // ====================================================================
    const executeTool = async (functionName: string, args: any) => {
      console.log(`[Zefio AI] 🔍 Tool Called: ${functionName}`, args)
      
      // Plugin Tools
      if (functionName === 'get_plugin_registry') {
        const masterRegistry = await storage.getItem('zefio:registry:master')
        return masterRegistry || { error: "Registry is empty. Sync DP first." }
      } 
      if (functionName === 'get_plugin_schema') {
        const pluginData = await storage.getItem(`zefio:plugin:${args.pluginName}`)
        return pluginData || { error: `Schema not found for ${args.pluginName}` }
      }
      
      // Topology & Flow Tools
      if (functionName === 'get_global_topology') {
        const globals = await storage.getItem('zefio:topology:globals')
        return globals || { error: "Global topology not found. Sync DP first." }
      }
      if (functionName === 'get_flow_list') {
        const flowList = await storage.getItem('zefio:registry:flows')
        return flowList || { error: "Flow registry is empty." }
      }
      if (functionName === 'get_flow_detail') {
        const flowDetail = await storage.getItem(`zefio:flow:${args.flowName}`)
        return flowDetail || { error: `Flow '${args.flowName}' not found.` }
      }

      return { error: "Unknown function" }
    }

    // Isolated function for Local Ollama execution
    const runOllamaFallback = async (prompt: string): Promise<string> => {
      console.log(`[Zefio AI] ⚠️ Gemini failed. Switching to Local Ollama (${config.ollamaModel})...`)
      
      // Utilize the existing OpenAI client but reroute the base URL to the Ollama server
      const ollamaClient = new OpenAI({
        baseURL: config.ollamaBaseUrl,
        apiKey: 'ollama-dummy-key', // Dummy key required by the OpenAI library
      })

      const openaiTools: any[] = [
        { type: "function", function: { name: "get_plugin_registry", description: "Get the master list of all available Zefio plugins.", parameters: { type: "object", properties: {}, required: [] } } },
        { type: "function", function: { name: "get_plugin_schema", description: "Get the detailed DTO schema for a specific plugin. Crucial for understanding valid 'config' properties.", parameters: { type: "object", properties: { pluginName: { type: "string" } }, required: ["pluginName"] } } },
        { type: "function", function: { name: "get_global_topology", description: "Get the global dictionary of profiles, telegrams, and endpoints.", parameters: { type: "object", properties: {}, required: [] } } },
        { type: "function", function: { name: "get_flow_list", description: "Get the list of all running flow names.", parameters: { type: "object", properties: {}, required: [] } } },
        { type: "function", function: { name: "get_flow_detail", description: "Get the detailed structure of a specific flow.", parameters: { type: "object", properties: { flowName: { type: "string" } }, required: ["flowName"] } } }
      ]

      let messages: any[] = [
        { role: "system", content: systemInstruction },
        { role: "user", content: prompt }
      ]

      let response = await ollamaClient.chat.completions.create({
        model: config.ollamaModel, // e.g., gemma4:e4b
        messages: messages,
        tools: openaiTools,
      })

      let responseMessage = response.choices[0].message

      // Ollama Tool Calling Loop
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
    // 4A. Google Gemini Integration (with Tool Calling)
    // ====================================================================
    if (config.aiProvider === 'gemini') {
      try {
        if (!config.geminiApiKey) throw new Error('GEMINI_API_KEY is missing.')
        console.log(`[Zefio AI] Starting Gemini Agent...`)

        const genAI = new GoogleGenerativeAI(config.geminiApiKey)
        
        // Gemini-specific tool schemas
        const geminiTools = [{
          functionDeclarations: [
            {
              name: "get_plugin_registry",
              description: "Get the master list of all available Zefio plugins."
            },
            {
              name: "get_plugin_schema",
              description: "Get the detailed DTO schema for a specific plugin. Crucial for understanding valid 'config' properties.",
              parameters: {
                type: SchemaType.OBJECT,
                properties: { pluginName: { type: SchemaType.STRING, description: "Exact name of the plugin" } },
                required: ["pluginName"]
              }
            },
            {
              name: "get_global_topology",
              description: "Get the global dictionary of profiles, telegrams, and endpoints currently loaded in the engine."
            },
            {
              name: "get_flow_list",
              description: "Get the list of all running flow names."
            },
            {
              name: "get_flow_detail",
              description: "Get the detailed YAML/JSON structure of a specific flow.",
              parameters: {
                type: SchemaType.OBJECT,
                properties: { flowName: { type: SchemaType.STRING, description: "Exact name of the flow (e.g., flow-main)" } },
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
        // Fallback to Ollama if Gemini API fails (e.g., quota exceeded)
        console.error('[Zefio AI] Gemini Error details:', geminiError.message)
        finalContent = await runOllamaFallback(body.prompt) 
      }
    } 

    // ====================================================================
    // 4B. OpenAI (ChatGPT) Integration (with Tool Calling)
    // ====================================================================
    else if (config.aiProvider === 'openai') {
      if (!config.openaiApiKey) throw new Error('OPENAI_API_KEY is missing.')
      console.log(`[Zefio AI] Starting OpenAI Agent...`)
      
      const openai = new OpenAI({ apiKey: config.openaiApiKey })

      // OpenAI-specific tool schemas
      const openaiTools: any[] = [
        { type: "function", function: { name: "get_plugin_registry", description: "Get the master list of all available Zefio plugins.", parameters: { type: "object", properties: {}, required: [] } } },
        { type: "function", function: { name: "get_plugin_schema", description: "Get the detailed DTO schema for a specific plugin. Crucial for understanding valid 'config' properties.", parameters: { type: "object", properties: { pluginName: { type: "string" } }, required: ["pluginName"] } } },
        { type: "function", function: { name: "get_global_topology", description: "Get the global dictionary of profiles, telegrams, and endpoints.", parameters: { type: "object", properties: {}, required: [] } } },
        { type: "function", function: { name: "get_flow_list", description: "Get the list of all running flow names.", parameters: { type: "object", properties: {}, required: [] } } },
        { type: "function", function: { name: "get_flow_detail", description: "Get the detailed structure of a specific flow.", parameters: { type: "object", properties: { flowName: { type: "string" } }, required: ["flowName"] } } }
      ]

      let messages: any[] = [
        { role: "system", content: systemInstruction },
        { role: "user", content: body.prompt }
      ]

      let response = await openai.chat.completions.create({
        model: "gpt-4o-mini", // Can be updated to gpt-4o depending on requirements
        messages: messages,
        tools: openaiTools,
        tool_choice: "auto",
        response_format: { type: "json_object" }
      })

      let responseMessage = response.choices[0].message

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
      throw new Error(`Invalid AI_PROVIDER: ${config.aiProvider}`)
    }

    // ====================================================================
    // 5. Post-processing (Bulletproof JSON to YAML)
    // ====================================================================
    if (!body.prompt.includes("알려줘") && !body.prompt.includes("뭐야") && !body.prompt.includes("있어")) {
      try {
        let jsonStr = finalContent;
        
        // 🚀 [V2 Sniper Extractor] Instead of a simple '{', it targets the '"flows"' keyword, which is the core of the Zefio schema.
        const flowsKeywordIndex = jsonStr.indexOf('"flows"');
        
        if (flowsKeywordIndex !== -1) {
          // 1. Set the starting point to the closest '{' immediately preceding the "flows" keyword. (Ignore fake JSON)
          const startIndex = jsonStr.lastIndexOf('{', flowsKeywordIndex);
          // 2. Set the end point to the very last '}' in the entire text.
          const endIndex = jsonStr.lastIndexOf('}');

          if (startIndex !== -1 && endIndex !== -1) {
            jsonStr = jsonStr.substring(startIndex, endIndex + 1);
            
            // 3. Parse the extracted pure JSON
            const jsonObject = JSON.parse(jsonStr);
            
            // 4. Convert to clean YAML
            finalContent = yaml.dump(jsonObject, { indent: 2, skipInvalid: true });
          } else {
            throw new Error("Could not find valid JSON boundaries.");
          }
        } else {
          throw new Error("No 'flows' keyword found in AI response.");
        }

      } catch (parseError: any) {
        console.error('[Zefio AI] JSON to YAML parsing failed. Raw content was:\n', finalContent);
        // Fallback exception handling: return the raw string to the UI if parsing fails
        return { 
          status: 500, 
          yaml: finalContent, 
          message: `AI generated invalid format: ${parseError.message}` 
        }
      }
    }

    return {
      status: 200,
      yaml: finalContent,
      message: `Processed successfully using ${config.aiProvider}.`
    }

  } catch (error: any) {
    console.error('[Zefio AI] Generation failed:', error.message)
    return { status: 500, message: `AI Server Error: ${error.message}` }
  }
})