package org.example.coom.compiler.profile;

import org.example.coom.compiler.CompilationContext;
import org.example.coom.compiler.diagnostic.Diagnostic;
import org.example.coom.compiler.diagnostic.Stage;

public final class BaseProfile implements CoomProfile {
    @Override
    public String name() {
        return "BASE";
    }

    @Override
    public void apply(CompilationContext context) {
        if (context.ast() == null) {
            context.addDiagnostic(Diagnostic.error(Stage.PROFILE, "BASE profile requires AST"));
            return;
        }

        context.addDiagnostic(Diagnostic.info(Stage.PROFILE, "BASE profile applied"));
    }
}
