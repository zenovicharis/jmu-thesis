package org.example.coom.compiler.profile;

import org.example.coom.compiler.CompilationContext;
import org.example.coom.compiler.NProfileGenerator;
import org.example.coom.compiler.diagnostic.Diagnostic;
import org.example.coom.compiler.diagnostic.Stage;

public final class NProfileStage implements CoomProfile {
    private final NProfileGenerator.NProfile level;

    public NProfileStage() {
        this(NProfileGenerator.NProfile.N_LIN);
    }

    public NProfileStage(NProfileGenerator.NProfile level) {
        this.level = level;
    }

    @Override
    public String name() {
        return "N";
    }

    @Override
    public void apply(CompilationContext context) {
        if (context.ast() == null) {
            context.addDiagnostic(Diagnostic.error(Stage.PROFILE, "N profile requires AST"));
            return;
        }

        String nProfileText = new NProfileGenerator().generate(context.ast(), level);
        context.artifacts().put("profile.nlin", nProfileText);
        context.addDiagnostic(Diagnostic.info(Stage.PROFILE, "N profile generated: " + level));
    }
}
