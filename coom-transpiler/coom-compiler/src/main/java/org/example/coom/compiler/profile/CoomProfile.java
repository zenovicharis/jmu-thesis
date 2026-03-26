package org.example.coom.compiler.profile;

import java.util.List;
import org.example.coom.compiler.CompilationContext;

public interface CoomProfile {
    String name();

    void apply(CompilationContext context);

    static List<CoomProfile> defaultPipeline() {
        return List.of(
                new BaseProfile(),
                new NProfileStage(),
                new CProfile()
        );
    }
}
