package org.example.coom.compiler.parse;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.jena.rdf.model.Model;
import org.example.coom.compiler.parse.antlr.CoomLexer;
import org.example.coom.compiler.parse.antlr.CoomParser;
import org.example.coom.compiler.rdf.AntlrRdfBuilder;

public final class CoomAntlrParserToRdf {

    public Model parseToRdf(String src, String productName, String baseIri) {
        CharStream input = CharStreams.fromString(src);

        CoomLexer lexer = new CoomLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        CoomParser parser = new CoomParser(tokens);

        // fail-fast (you can replace with your own listener)
        parser.removeErrorListeners();
        parser.addErrorListener(new DiagnosticErrorListener());

        ParseTree tree = parser.coomFile();

        AntlrRdfBuilder visitor = new AntlrRdfBuilder(baseIri, productName);
        visitor.visit(tree);

        return visitor.getModel();
    }
}
