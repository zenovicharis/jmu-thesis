package org.example.coom.compiler.validation;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.Shapes;

public final class ShaclValidation {

    public record Result(boolean conforms, String reportTurtle) {}

    private ShaclValidation() {}

    public static Result validate(Model dataModel, Model shapesModel) {
        Shapes shapes = Shapes.parse(shapesModel.getGraph());
        ValidationReport report = ShaclValidator.get().validate(shapes, dataModel.getGraph());
        String reportTtl = "";
        if (report.getModel() != null) {
            reportTtl = RDFWriter.create()
                    .lang(Lang.TURTLE)
                    .source(report.getModel())
                    .asString();
        }
        return new Result(report.conforms(), reportTtl);
    }

    public static Model loadShapes(String shapesPath) throws Exception {
        Model shapes = ModelFactory.createDefaultModel();
        Lang lang = RDFLanguages.filenameToLang(shapesPath);
        if (lang == null) lang = Lang.TURTLE;
        try (InputStream in = Files.newInputStream(Path.of(shapesPath))) {
            RDFDataMgr.read(shapes, in, lang);
        }
        return shapes;
    }

    public static Model loadDefaultFromClasspath() throws Exception {
        Model shapes = ModelFactory.createDefaultModel();
        try (InputStream in = ShaclValidation.class.getResourceAsStream("/shapes/default.shapes.ttl")) {
            if (in == null) {
                return shapes; // empty shapes graph -> conforms vacuously
            }
            RDFDataMgr.read(shapes, in, Lang.TURTLE);
        }
        return shapes;
    }

    public static Model loadPresetFromClasspath(String presetName) throws Exception {
        if (presetName == null || presetName.isBlank()) {
            return loadDefaultFromClasspath();
        }
        String resource = "/shapes/" + presetName + ".shacl.ttl";
        Model shapes = ModelFactory.createDefaultModel();
        try (InputStream in = ShaclValidation.class.getResourceAsStream(resource)) {
            if (in == null) {
                return loadDefaultFromClasspath();
            }
            RDFDataMgr.read(shapes, in, Lang.TURTLE);
        }
        return shapes;
    }
}
