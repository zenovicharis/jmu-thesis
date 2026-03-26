package org.example.coom.compiler.pipeline;

import org.example.coom.compiler.CompilationContext;

public interface PipelineStage {
    String name();

    void apply(CompilationContext context);
}
