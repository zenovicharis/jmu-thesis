package org.example.coom.compiler.pipeline;

import org.antlr.v4.runtime.tree.ParseTree;
import org.example.coom.compiler.CompilationContext;
import org.example.coom.compiler.diagnostic.Diagnostic;
import org.example.coom.compiler.diagnostic.Stage;
import org.example.coom.compiler.parse.CoomAntlrParser;

public final class ParseStage implements PipelineStage {
    @Override
    public String name() {
        return "parse";
    }

    @Override
    public void apply(CompilationContext context) {
        try {
            CoomAntlrParser parser = new CoomAntlrParser(context.options().traceStages());
            ParseTree tree = parser.parse(context.sourceText());
            context.setParseTree(tree);
            context.addDiagnostic(Diagnostic.info(Stage.PARSE, "ANTLR parse completed"));
        } catch (RuntimeException ex) {
            context.addDiagnostic(Diagnostic.error(Stage.PARSE, ex.getMessage()));
        }
    }
}
