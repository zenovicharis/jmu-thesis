package org.example.coom.compiler;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFWriter;
import org.example.coom.compiler.model.CoomAst;
import org.example.coom.compiler.parse.CoomRegexParser;
import org.example.coom.compiler.parse.CoomAntlrParserAdapter;
import org.example.coom.compiler.parse.ICoomParser;
import org.example.coom.compiler.rdf.RdfBuilder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Public API for COOM -> RDF transpilation.
 * - Default output: Turtle
 * - Supports RDF/XML, N-Triples, JSON-LD via RdfFormat.
 */
public class CoomTranspiler {

    /** Result used by the web controller for preview + download. */
    public record TranspileResult(String content, String contentType, String filename) { }

    /** Default Turtle output (backwards-compat). */
    public String transpile(byte[] fileContent, String fileName) {
        return transpileTo(fileContent, fileName, RdfFormat.TURTLE).content();
    }

    /** Existing signature remains unchanged (defaults to non-ANTLR). */
    public TranspileResult transpileTo(byte[] fileContent, String fileName, RdfFormat fmt) {
        // Default behavior preserved; you can also read from a system property if desired:
        boolean useAntlr = Boolean.parseBoolean(System.getProperty("coom.useAntlr", "false"));
        return transpileTo(fileContent, fileName, fmt, useAntlr);
    }

    /** New overload: caller can explicitly choose ANTLR parser. */
    public TranspileResult transpileTo(byte[] fileContent, String fileName, RdfFormat fmt, boolean useAntlr) {
        String src = new String(fileContent, StandardCharsets.UTF_8);
        String base = Util.stripExt(fileName);
        String productName = base.isBlank() ? "Product" : base;

        System.out.println("COOM DEBUG: src length=" + src.length());
        System.out.println("COOM DEBUG: first 200 chars:\n" + src.substring(0, Math.min(200, src.length())));


        // 1) Parse COOM -> AST (switchable)
        ICoomParser parser = useAntlr ? new CoomAntlrParserAdapter() : new CoomRegexParser();
        CoomAst.CoomModel ast = parser.parse(src, productName);

        System.out.println("COOM DEBUG: useAntlr=" + useAntlr);
        System.out.println("COOM DEBUG: parser=" + parser.getClass().getName());

        // 2) AST -> RDF (Jena model)
        Model model = new RdfBuilder().build(ast, productName);

        // 3) Serialize
        String text = write(model, fmt);
        String outName = (base.isBlank() ? "model" : base) + "." + fmt.ext;

        return new TranspileResult(text, fmt.contentType, outName);
    }

    private String write(Model model, RdfFormat fmt) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (fmt == RdfFormat.JSONLD) {
                // prettier JSON-LD
                RDFWriter.create()
                        .source(model)
                        .format(RDFFormat.JSONLD_PRETTY)
                        .output(out);
            } else {
                RDFDataMgr.write(out, model, fmt.lang);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize RDF as " + fmt, e);
        }
    }
}
