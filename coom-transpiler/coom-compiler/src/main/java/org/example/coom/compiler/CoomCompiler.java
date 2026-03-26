package org.example.coom.compiler;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.example.coom.compiler.diagnostic.Diagnostic;
import org.example.coom.compiler.pipeline.AstStage;
import org.example.coom.compiler.pipeline.CompilerPipeline;
import org.example.coom.compiler.pipeline.ParseStage;
import org.example.coom.compiler.pipeline.ProfileStage;
import org.example.coom.compiler.pipeline.RdfStage;
import org.example.coom.compiler.pipeline.SerializeStage;
import org.example.coom.compiler.pipeline.ValidateStage;

public final class CoomCompiler {
    public CompilationResult compile(byte[] sourceBytes, String fileName, CompilationOptions options) {
        String src = new String(sourceBytes, StandardCharsets.UTF_8);
        String base = Util.stripExt(fileName);
        String productName = options.productNameOverride();
        if (productName == null || productName.isBlank()) {
            productName = base.isBlank() ? "Product" : base;
        }

        CompilationContext context = new CompilationContext(sourceBytes, src, fileName, productName, options);

        CompilerPipeline pipeline = new CompilerPipeline(List.of(
                new ParseStage(),
                new AstStage(),
                new ProfileStage(options.profiles()),
                new RdfStage(),
                new ValidateStage(),
                new SerializeStage()
        ));

        pipeline.execute(context);

        return new CompilationResult(
                context.output() == null ? "" : context.output(),
                context.outputContentType(),
                context.outputFilename(),
                context.diagnostics(),
                context.conforms(),
                context.validationReport()
        );
    }

    public CompilationResult compile(String source, String fileName, CompilationOptions options) {
        return compile(source.getBytes(StandardCharsets.UTF_8), fileName, options);
    }

    public List<Diagnostic> diagnosticsFor(byte[] sourceBytes, String fileName, CompilationOptions options) {
        return compile(sourceBytes, fileName, options).diagnostics();
    }
}
