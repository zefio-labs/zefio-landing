// server/utils/zefio-compiler.ts
import yaml from 'js-yaml'

/**
 * Shared core compilation engine for Zefio DSL blueprints.
 * Flattens profile and endpoint map references into self-contained values.
 */
export function compileAndFlattenFlows(flows: any[], mergedGlobals: any): string {
  if (!flows || !Array.isArray(flows)) {
    throw new Error("Invalid topology data. Core 'flows' array is missing.")
  }

  for (const flow of flows) {
    // 1. Flatten Ingress configuration
    if (flow.ingress) {
      if (flow.ingress.profile && mergedGlobals.profiles[flow.ingress.profile]) {
        const matchedProfile = mergedGlobals.profiles[flow.ingress.profile]
        if (matchedProfile.exchangePattern) {
          flow.ingress.exchangePattern = matchedProfile.exchangePattern || flow.ingress.exchangePattern
        }
        flow.ingress.config = { ...matchedProfile.config, ...flow.ingress.config }
        delete flow.ingress.profile
      }
    }

    // 2. Recursively flatten step trees
    if (flow.steps) flattenStepsArray(flow.steps, mergedGlobals)
    if (flow['on-error']) {
      for (const errNode of flow['on-error']) {
        if (errNode.steps) flattenStepsArray(errNode.steps, mergedGlobals)
      }
    }
  }

  // 💡 Clean Fix: Serialize ONLY the pure fully-flattened flows without appending global maps inside text block
  return yaml.dump({ flows: flows })
}

/**
 * Internal recursive crawler utilizing optimized O(1) Map lookups.
 */
function flattenStepsArray(steps: any[], globals: any) {
  for (let step of steps) {
    // Resolve Root-Level refModuleName
    if (step.refModuleName && globals.endpoints) {
      const matchedEndpoint = globals.endpoints[step.refModuleName]
      if (matchedEndpoint) {
        step.type = matchedEndpoint.type
        step.label = matchedEndpoint.label
        step.telegram = matchedEndpoint.telegram
        step.profile = matchedEndpoint.profile || step.profile
        if (matchedEndpoint.exchangePattern) {
          step.exchangePattern = matchedEndpoint.exchangePattern
        }
        step.retry = { ...matchedEndpoint.retry, ...step.retry }
        step.config = { ...matchedEndpoint.config, ...step.config }

        if (step.profile && globals.profiles?.[step.profile]) {
          const matchedProfile = globals.profiles[step.profile]
          if (matchedProfile.exchangePattern) {
            step.exchangePattern = matchedProfile.exchangePattern || step.exchangePattern
          }
          step.config = { ...matchedProfile.config, ...step.config }
          delete step.profile
        }
        delete step.refModuleName
      }
    }

    // Resolve Inner Interceptor Routing Rules (e.g., SpELRouterInterceptor)
    if (step.config?.routingRules && Array.isArray(step.config.routingRules) && globals.endpoints) {
      for (let rule of step.config.routingRules) {
        if (rule.refModuleName) {
          const matchedEndpoint = globals.endpoints[rule.refModuleName]
          if (matchedEndpoint) {
            rule.type = matchedEndpoint.type
            rule.telegram = matchedEndpoint.telegram
            rule.profile = matchedEndpoint.profile || rule.profile
            if (matchedEndpoint.exchangePattern) {
              rule.exchangePattern = matchedEndpoint.exchangePattern
            }
            rule.retry = { ...matchedEndpoint.retry, ...rule.retry }
            rule.config = { ...matchedEndpoint.config, ...rule.config }

            if (rule.profile && globals.profiles?.[rule.profile]) {
              const matchedProfile = globals.profiles[rule.profile]
              if (matchedProfile.exchangePattern) {
                rule.exchangePattern = matchedProfile.exchangePattern || rule.exchangePattern
              }
              rule.config = { ...matchedProfile.config, ...rule.config }
              delete rule.profile
            }
            delete rule.refModuleName
          }
        }
      }
    }

    // Resolve Standard Profiles
    if (step.profile && globals.profiles?.[step.profile]) {
      const matchedProfile = globals.profiles[step.profile]
      if (matchedProfile.exchangePattern) {
        step.exchangePattern = matchedProfile.exchangePattern || step.exchangePattern
      }
      step.config = { ...matchedProfile.config, ...step.config }
      delete step.profile
    }

    if (step.steps) flattenStepsArray(step.steps, globals)
    if (step['fallback-steps']) flattenStepsArray(step['fallback-steps'], globals)
    if (step.cases) {
      for (const c of step.cases) {
        if (c.steps) flattenStepsArray(c.steps, globals)
      }
    }
    if (step.defaultSteps) flattenStepsArray(step.defaultSteps, globals)
  }
}