package org.example.coom.compiler;

import java.util.List;
import org.example.coom.compiler.diagnostic.Diagnostic;

public record CompilationResult(
        String content,
        String contentType,
        String filename,
        List<Diagnostic> diagnostics,
        boolean conforms,
        String validationReport
) {}
