// server/api/generate.post.ts
import { defineEventHandler, readBody } from 'h3'
import { useRuntimeConfig, useStorage } from '#imports'
import { GoogleGenerativeAI, SchemaType } from '@google/generative-ai'
import OpenAI from 'openai'

export default defineEventHandler(async (event) => {
  const body = await readBody(event)
  const config = useRuntimeConfig()
  const storage = useStorage('db') // Access Nitro Storage

  if (!body || !body.prompt) {
    return { status: 400, message: 'Prompt is required.' }
  }

  try {
    // ====================================================================
    // 1. Common System Prompt (AIOps Rules)
    // ====================================================================
    const systemInstruction = `
You are 'Zefio AI Architect', an expert assistant for the SEDA Engine.
Your primary goal is to help users build and modify YAML pipeline configurations.

CRITICAL RULES:
1. If the user asks about plugins (e.g., "What Ingresses are available?", "알려줘"), use 'get_plugin_registry' to answer conversationally.
2. If the user asks to modify an existing flow, YOU MUST use 'get_flow_list' and 'get_flow_detail' to read the current runtime structure before generating YAML.
3. If the user mentions profiles or telegrams, use 'get_global_topology' to check the available dictionary. 
4. IMPORTANT: When setting 'telegram' or 'profile' in a flow, use the exact Key string from the globals dictionary. DO NOT inline/copy the global schema inside the flow YAML. Maintain the reference structure.
5. If the user asks to create or generate a pipeline, output ONLY valid YAML. No markdown blocks like \`\`\`yaml, no conversational text.
`
    let finalContent = ''

    // ====================================================================
    // 2. Tool Execution Logic (Storage Access)
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
        { type: "function", function: { name: "get_plugin_schema", description: "Get the detailed DTO schema for a specific plugin.", parameters: { type: "object", properties: { pluginName: { type: "string" } }, required: ["pluginName"] } } },
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
    // 3A. Google Gemini Integration (with Tool Calling)
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
              description: "Get the detailed DTO schema for a specific plugin.",
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
    // 3B. OpenAI (ChatGPT) Integration (with Tool Calling)
    // ====================================================================
    else if (config.aiProvider === 'openai') {
      if (!config.openaiApiKey) throw new Error('OPENAI_API_KEY is missing.')
      console.log(`[Zefio AI] Starting OpenAI Agent...`)
      
      const openai = new OpenAI({ apiKey: config.openaiApiKey })

      // OpenAI-specific tool schemas
      const openaiTools: any[] = [
        { type: "function", function: { name: "get_plugin_registry", description: "Get the master list of all available Zefio plugins.", parameters: { type: "object", properties: {}, required: [] } } },
        { type: "function", function: { name: "get_plugin_schema", description: "Get the detailed DTO schema for a specific plugin.", parameters: { type: "object", properties: { pluginName: { type: "string" } }, required: ["pluginName"] } } },
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
    // 4. Post-processing (YAML Cleansing)
    // ====================================================================
    if (!body.prompt.includes("알려줘") && !body.prompt.includes("뭐야") && !body.prompt.includes("있어")) {
      finalContent = finalContent.replace(/```yaml\n?/gi, '').replace(/```\n?/g, '').trim()
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