package org.example.coom.compiler.pipeline;

import java.util.List;
import org.example.coom.compiler.CompilationContext;
import org.example.coom.compiler.profile.CoomProfile;

public final class ProfileStage implements PipelineStage {
    private final List<CoomProfile> profiles;

    public ProfileStage(List<CoomProfile> profiles) {
        this.profiles = List.copyOf(profiles);
    }

    @Override
    public String name() {
        return "profile";
    }

    @Override
    public void apply(CompilationContext context) {
        for (CoomProfile profile : profiles) {
            try {
                profile.apply(context);
            } catch (RuntimeException ex) {
                String msg = ex.getMessage();
                String detail = ex.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
                context.addDiagnostic(org.example.coom.compiler.diagnostic.Diagnostic.error(
                        org.example.coom.compiler.diagnostic.Stage.PROFILE,
                        profile.name() + " profile failed: " + detail));
                return;
            }
            if (context.hasError()) {
                return;
            }
        }
    }
}
