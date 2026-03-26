package org.example.coom.compiler.pipeline;

import org.example.coom.compiler.CompilationContext;
import org.example.coom.compiler.diagnostic.Diagnostic;
import org.example.coom.compiler.diagnostic.Stage;
import org.example.coom.compiler.model.CoomAst;
import org.example.coom.compiler.parse.CoomAstBuilder;

public final class AstStage implements PipelineStage {
    @Override
    public String name() {
        return "ast";
    }

    @Override
    public void apply(CompilationContext context) {
        if (context.parseTree() == null) {
            context.addDiagnostic(Diagnostic.error(Stage.AST, "AST stage requires parse tree"));
            return;
        }

        CoomAstBuilder builder = new CoomAstBuilder(context.productName());
        builder.visit(context.parseTree());
        CoomAst.CoomModel ast = builder.getModel();
        context.setAst(ast);
        context.addDiagnostic(Diagnostic.info(Stage.AST, "AST built"));
    }
}
