package org.example.coom.compiler;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.example.coom.compiler.model.CoomAst;
import org.example.coom.compiler.parse.CoomRegexParser;
import org.example.coom.compiler.parse.ICoomParser;
import org.example.coom.compiler.profile.NLinToRdf;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class CoomNlinRdfCli {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: CoomNlinRdfCli <file.coom>");
            System.exit(2);
        }

        Path p = Path.of(args[0]);
        byte[] bytes = Files.readAllBytes(p);

        String src = new String(bytes, StandardCharsets.UTF_8);
        String productName = Util.stripExt(p.getFileName().toString());
        if (productName.isBlank()) productName = "Product";

        // Parse COOM -> AST (use regex parser for reliability)
        ICoomParser parser = new CoomRegexParser();
        CoomAst.CoomModel ast = parser.parse(src, productName);

        // AST -> N-LIN
        String nlin = new NProfileGenerator().generate(ast, NProfileGenerator.NProfile.N_LIN);

        // N-LIN -> RDF
        List<String> lines = Arrays.stream(nlin.split("\\R")).toList();
        Model model = new NLinToRdf().toRdf(lines);

        // Print Turtle
        RDFDataMgr.write(System.out, model, RDFFormat.TURTLE_PRETTY);
    }
}
