package org.example.coom.compiler.profile;

import org.example.coom.compiler.CompilationContext;
import org.example.coom.compiler.diagnostic.Diagnostic;
import org.example.coom.compiler.diagnostic.Stage;

public final class CProfile implements CoomProfile {
    @Override
    public String name() {
        return "C";
    }

    @Override
    public void apply(CompilationContext context) {
        if (context.ast() == null) {
            context.addDiagnostic(Diagnostic.error(Stage.PROFILE, "C profile requires AST"));
            return;
        }

        int count = context.ast().constraints.size();
        context.addDiagnostic(Diagnostic.info(Stage.PROFILE, "C profile applied: constraints=" + count));
    }
}
