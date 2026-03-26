package org.example.coom.compiler.pipeline;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFWriter;
import org.example.coom.compiler.CompilationContext;
import org.example.coom.compiler.Util;
import org.example.coom.compiler.diagnostic.Diagnostic;
import org.example.coom.compiler.diagnostic.Stage;

public final class SerializeStage implements PipelineStage {
    @Override
    public String name() {
        return "serialize";
    }

    @Override
    public void apply(CompilationContext context) {
        if (context.rdfModel() == null) {
            context.addDiagnostic(Diagnostic.error(Stage.SERIALIZE, "Serialize stage requires RDF model"));
            return;
        }

        org.example.coom.compiler.RdfFormat fmt = context.options().format();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (fmt == org.example.coom.compiler.RdfFormat.JSONLD) {
                RDFWriter.create()
                        .source(context.rdfModel())
                        .format(RDFFormat.JSONLD_PRETTY)
                        .output(out);
            } else {
                RDFDataMgr.write(out, context.rdfModel(), fmt.lang);
            }

            String base = Util.stripExt(context.fileName());
            String outName = (base.isBlank() ? "model" : base) + "." + fmt.ext;
            context.setOutput(out.toString(StandardCharsets.UTF_8), fmt.contentType, outName);
            context.addDiagnostic(Diagnostic.info(Stage.SERIALIZE, "RDF serialized"));
        } catch (Exception ex) {
            context.addDiagnostic(Diagnostic.error(Stage.SERIALIZE, ex.getMessage()));
        }
    }
}
