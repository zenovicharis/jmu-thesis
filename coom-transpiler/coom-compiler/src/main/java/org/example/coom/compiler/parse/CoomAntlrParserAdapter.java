package org.example.coom.compiler.parse;

import org.antlr.v4.runtime.tree.ParseTree;
import org.example.coom.compiler.model.CoomAst;
import org.example.coom.compiler.parse.antlr.CoomParser;

public final class CoomAntlrParserAdapter implements ICoomParser {

    @Override
    public CoomAst.CoomModel parse(String src, String productName) {
        ParseTree tree = new CoomAntlrParser(false).parse(src);

        // Build AST from parse tree
        CoomAstBuilder builder = new CoomAstBuilder(productName);
        builder.visit(tree);

        return builder.getModel();
    }
}
