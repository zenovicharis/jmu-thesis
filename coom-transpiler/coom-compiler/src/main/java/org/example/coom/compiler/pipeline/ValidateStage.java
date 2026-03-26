package org.example.coom.compiler.pipeline;

import org.apache.jena.rdf.model.Model;
import org.example.coom.compiler.CompilationContext;
import org.example.coom.compiler.diagnostic.Diagnostic;
import org.example.coom.compiler.diagnostic.Stage;
import org.example.coom.compiler.validation.ShaclValidation;
import java.util.List;
import org.example.coom.compiler.diagnostic.Severity;

public final class ValidateStage implements PipelineStage {
    @Override
    public String name() {
        return "validate";
    }

    @Override
    public void apply(CompilationContext context) {
        if (!context.options().validate()) return;
        Model data = context.rdfModel();
        if (data == null) {
            context.addDiagnostic(Diagnostic.error(Stage.VALIDATE,
                    "No RDF model available for SHACL validation."));
            return;
        }

        try {
            List<String> paths = context.options().shapesPaths();
            List<String> effective = (paths == null || paths.isEmpty())
                    ? List.of("default")
                    : paths.stream().filter(p -> p != null && !p.isBlank()).toList();

            boolean overall = true;
            StringBuilder combinedReport = new StringBuilder();

            for (String path : effective) {
                Model currentShapes = loadShapesModel(path);
                ShaclValidation.Result r = ShaclValidation.validate(data, currentShapes);
                overall = overall && r.conforms();

                String label = friendlyLabel(path);
                context.addDiagnostic(new Diagnostic(
                        r.conforms() ? Severity.INFO : Severity.ERROR,
                        Stage.VALIDATE,
                        "SHACL " + label + (r.conforms() ? " passed" : " failed")
                ));

                if (r.reportTurtle() != null && !r.reportTurtle().isBlank()) {
                    combinedReport.append("### ").append(label).append("\n")
                            .append(r.reportTurtle()).append("\n");
                }
            }

            // store the aggregated outcome for the UI
            context.setValidationResult(overall, combinedReport.toString());
            if (!overall) {
                context.addDiagnostic(new Diagnostic(
                        Severity.ERROR,
                        Stage.VALIDATE,
                        "SHACL validation failed."
                ));
            }
            return;
        } catch (Exception e) {
            context.addDiagnostic(new Diagnostic(
                    Severity.ERROR,
                    Stage.VALIDATE,
                    "Failed to load SHACL shapes: " + e.getMessage()
            ));
            return;
        }
    }

    private Model loadShapesModel(String path) throws Exception {
        if (path == null || path.isBlank() || path.equals("default")) {
            return ShaclValidation.loadDefaultFromClasspath();
        }
        if (path.equals("syntactic-core") || path.equals("semantic-consistency") || path.equals("profile-refinements")) {
            return ShaclValidation.loadPresetFromClasspath(path);
        }
        return ShaclValidation.loadShapes(path);
    }

    private String friendlyLabel(String path) {
        if (path == null || path.isBlank() || path.equals("default")) return "Default";
        return path.replace('-', ' ');
    }
}
