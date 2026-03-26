package org.example.coom.compiler.pipeline;

import java.util.List;
import org.example.coom.compiler.CompilationContext;
import org.example.coom.compiler.diagnostic.Diagnostic;
import org.example.coom.compiler.diagnostic.Stage;

public final class CompilerPipeline {
    private final List<PipelineStage> stages;

    public CompilerPipeline(List<PipelineStage> stages) {
        this.stages = List.copyOf(stages);
    }

    public void execute(CompilationContext context) {
        for (PipelineStage stage : stages) {
            if (context.hasError()) {
                return;
            }
            try {
                stage.apply(context);
            } catch (RuntimeException ex) {
                String msg = ex.getMessage();
                String detail = ex.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
                context.addDiagnostic(Diagnostic.error(stageToDiagnostic(stage.name()), detail));
                return;
            }
        }
    }

    private static Stage stageToDiagnostic(String name) {
        return switch (name) {
            case "parse" -> Stage.PARSE;
            case "ast" -> Stage.AST;
            case "profile" -> Stage.PROFILE;
            case "rdf" -> Stage.RDF;
            case "validate" -> Stage.VALIDATE;
            case "serialize" -> Stage.SERIALIZE;
            default -> Stage.PROFILE;
        };
    }
}
