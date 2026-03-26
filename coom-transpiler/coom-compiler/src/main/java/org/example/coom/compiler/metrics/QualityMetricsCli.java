package org.example.coom.compiler.metrics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sys.JenaSystem;
import org.apache.jena.vocabulary.RDF;
import org.example.coom.compiler.CompilationOptions;
import org.example.coom.compiler.CompilationResult;
import org.example.coom.compiler.CoomCompiler;
import org.example.coom.compiler.RdfFormat;
import org.example.coom.compiler.validation.ShaclValidation;

/**
 * Collects quality-metric values for one or more COOM files and prints CSV rows
 * that can be copied into thesis benchmark tables.
 */
public final class QualityMetricsCli {

    private static final List<String> SHAPE_PRESETS = List.of(
            "syntactic-core",
            "semantic-consistency",
            "profile-refinements"
    );

    private static final int QUERY_REPEATS = 5;

    private static final String PREFIXES = String.join("\n",
            "PREFIX coom: <http://example.com/schema/coom#>",
            "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>",
            ""
    );

    private static final String REACH_QUERY = PREFIXES +
            "SELECT ?product ?structure WHERE {\n" +
            "  ?product a coom:Product ;\n" +
            "           coom:hasStructure+ ?structure .\n" +
            "}";

    private static final String SANITY_ASK = PREFIXES +
            "ASK { ?s coom:hasStructure ?s }";

    private static final String CROSS_ATTR_QUERY = PREFIXES +
            "SELECT ?product ?constraint WHERE {\n" +
            "  ?product a coom:Product ; coom:hasConstraint ?constraint .\n" +
            "  ?constraint a coom:Constraint ;\n" +
            "              coom:condition ?cond ;\n" +
            "              coom:expression ?expr .\n" +
            "}";

    private static final String AGG_QUERY = PREFIXES +
            "SELECT ?product (COUNT(DISTINCT ?x) AS ?cnt) WHERE {\n" +
            "  ?product a coom:Product .\n" +
            "  { ?product coom:hasAttribute ?x } UNION { ?product coom:hasStructure ?x }\n" +
            "}\n" +
            "GROUP BY ?product";

    private QualityMetricsCli() {}

    public static void main(String[] args) throws Exception {
        JenaSystem.init();

        if (args.length == 0 || hasHelp(args)) {
            printUsage();
            System.exit(1);
        }

        List<Path> inputs = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("-")) {
                continue;
            }
            inputs.add(Path.of(arg));
        }

        if (inputs.isEmpty()) {
            printUsage();
            System.exit(2);
        }

        System.out.println("Model,Triples,Shapes,SHACL_ms,Reach_ms,Sanity_ms,CrossAttr_ms,Agg_ms,Conforms,Violations");
        for (Path input : inputs) {
            Row row = collect(input);
            System.out.println(row.toCsv());
        }
    }

    private static Row collect(Path input) throws Exception {
        if (!Files.exists(input)) {
            throw new IllegalArgumentException("Input file not found: " + input);
        }

        byte[] bytes = Files.readAllBytes(input);
        CompilationOptions options = CompilationOptions.builder()
                .format(RdfFormat.TURTLE)
                .validate(false)
                .build();

        CompilationResult compiled = new CoomCompiler().compile(bytes, input.getFileName().toString(), options);
        if (compiled.content() == null || compiled.content().isBlank()) {
            throw new IllegalStateException("Compilation produced empty RDF for " + input);
        }

        Model dataModel = ModelFactory.createDefaultModel();
        RDFParser.fromString(compiled.content()).lang(Lang.TURTLE).parse(dataModel);

        long triples = dataModel.size();
        long shapes = countNodeShapes();

        warmUp(dataModel);

        double shaclMs = 0.0;
        boolean conforms = true;
        int violations = 0;

        for (String preset : SHAPE_PRESETS) {
            Model shapeModel = ShaclValidation.loadPresetFromClasspath(preset);
            long start = System.nanoTime();
            ShaclValidation.Result result = ShaclValidation.validate(dataModel, shapeModel);
            long end = System.nanoTime();

            shaclMs += nanosToMillis(end - start);
            conforms = conforms && result.conforms();
            violations += countValidationResults(result.reportTurtle());
        }

        double reachMs = medianSelectRuntime(dataModel, REACH_QUERY);
        double sanityMs = medianAskRuntime(dataModel, SANITY_ASK);
        double crossAttrMs = medianSelectRuntime(dataModel, CROSS_ATTR_QUERY);
        double aggMs = medianSelectRuntime(dataModel, AGG_QUERY);

        return new Row(
                input.getFileName().toString(),
                triples,
                shapes,
                shaclMs,
                reachMs,
                sanityMs,
                crossAttrMs,
                aggMs,
                conforms,
                violations
        );
    }

    private static void warmUp(Model dataModel) throws Exception {
        for (String preset : SHAPE_PRESETS) {
            Model shapeModel = ShaclValidation.loadPresetFromClasspath(preset);
            ShaclValidation.validate(dataModel, shapeModel);
        }
        try (QueryExecution qe = QueryExecutionFactory.create(REACH_QUERY, dataModel)) {
            ResultSet rs = qe.execSelect();
            while (rs.hasNext()) {
                rs.next();
            }
        }
        try (QueryExecution qe = QueryExecutionFactory.create(SANITY_ASK, dataModel)) {
            qe.execAsk();
        }
        try (QueryExecution qe = QueryExecutionFactory.create(CROSS_ATTR_QUERY, dataModel)) {
            ResultSet rs = qe.execSelect();
            while (rs.hasNext()) {
                rs.next();
            }
        }
        try (QueryExecution qe = QueryExecutionFactory.create(AGG_QUERY, dataModel)) {
            ResultSet rs = qe.execSelect();
            while (rs.hasNext()) {
                rs.next();
            }
        }
    }

    private static long countNodeShapes() throws Exception {
        long count = 0;
        for (String preset : SHAPE_PRESETS) {
            Model shapeModel = ShaclValidation.loadPresetFromClasspath(preset);
            count += shapeModel.listStatements(
                    null,
                    RDF.type,
                    shapeModel.createResource("http://www.w3.org/ns/shacl#NodeShape")
            ).toList().size();
        }
        return count;
    }

    private static int countValidationResults(String reportTurtle) {
        if (reportTurtle == null || reportTurtle.isBlank()) {
            return 0;
        }
        Model reportModel = ModelFactory.createDefaultModel();
        RDFParser.fromString(reportTurtle).lang(Lang.TURTLE).parse(reportModel);
        return reportModel.listStatements(
                null,
                RDF.type,
                reportModel.createResource("http://www.w3.org/ns/shacl#ValidationResult")
        ).toList().size();
    }

    private static double medianSelectRuntime(Model model, String query) {
        double[] samples = new double[QUERY_REPEATS];
        for (int i = 0; i < QUERY_REPEATS; i++) {
            long start = System.nanoTime();
            try (QueryExecution qe = QueryExecutionFactory.create(query, model)) {
                ResultSet rs = qe.execSelect();
                while (rs.hasNext()) {
                    rs.next();
                }
            }
            long end = System.nanoTime();
            samples[i] = nanosToMillis(end - start);
        }
        Arrays.sort(samples);
        return samples[samples.length / 2];
    }

    private static double medianAskRuntime(Model model, String query) {
        double[] samples = new double[QUERY_REPEATS];
        for (int i = 0; i < QUERY_REPEATS; i++) {
            long start = System.nanoTime();
            try (QueryExecution qe = QueryExecutionFactory.create(query, model)) {
                qe.execAsk();
            }
            long end = System.nanoTime();
            samples[i] = nanosToMillis(end - start);
        }
        Arrays.sort(samples);
        return samples[samples.length / 2];
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static boolean hasHelp(String[] args) {
        for (String arg : args) {
            if ("-h".equals(arg) || "--help".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void printUsage() {
        System.out.println("Usage: QualityMetricsCli <file1.coom> [file2.coom ...]");
        System.out.println("Prints CSV metrics rows for SHACL and SPARQL benchmark tables.");
    }

    private record Row(
            String model,
            long triples,
            long shapes,
            double shaclMs,
            double reachMs,
            double sanityMs,
            double crossAttrMs,
            double aggMs,
            boolean conforms,
            int violations
    ) {
        String toCsv() {
            return String.format(
                    Locale.ROOT,
                    "%s,%d,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%s,%d",
                    model,
                    triples,
                    shapes,
                    shaclMs,
                    reachMs,
                    sanityMs,
                    crossAttrMs,
                    aggMs,
                    conforms,
                    violations
            );
        }
    }
}
