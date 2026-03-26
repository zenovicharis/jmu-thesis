package org.example.coom.compiler.pipeline;

import org.apache.jena.rdf.model.Model;
import org.example.coom.compiler.CompilationContext;
import org.example.coom.compiler.diagnostic.Diagnostic;
import org.example.coom.compiler.diagnostic.Stage;
import org.example.coom.compiler.rdf.RdfBuilder;

public final class RdfStage implements PipelineStage {
    @Override
    public String name() {
        return "rdf";
    }

    @Override
    public void apply(CompilationContext context) {
        if (context.ast() == null) {
            context.addDiagnostic(Diagnostic.error(Stage.RDF, "RDF stage requires AST"));
            return;
        }

        Model model = new RdfBuilder().build(context.ast(), context.productName());
        context.setRdfModel(model);
        context.addDiagnostic(Diagnostic.info(Stage.RDF, "RDF model built"));
    }
}
