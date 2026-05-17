package io.zefio.core;

/**
 * The master controller for the Zefio Core engine.
 * Responsible for the overall lifecycle, including orchestrating
 * configuration loading, starting all defined flows, and executing
 * shutdown sequences for both individual flows and the core system.
 */
public interface ZefioCoreService {
    void execute();
    void startAllFlows() throws Exception;
    void shutdownFlowsOnly();
    void shutdown();
}
