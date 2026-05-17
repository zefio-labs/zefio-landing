package io.zefio.core.engine.processor.dto;

import io.zefio.core.engine.processor.Processor;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * Represents a conditional execution branch within a Switch processor.
 * It contains a SpEL expression to evaluate and the corresponding
 * sequence of processors to execute if the condition is met.
 */
@Getter
@AllArgsConstructor
public class SwitchBranch {

    private final String condition;      // SpEL expression (e.g., "#{body['AMOUNT'] > 10000}")

    private final List<Processor> steps; // Async chain to execute if the condition is true
}
