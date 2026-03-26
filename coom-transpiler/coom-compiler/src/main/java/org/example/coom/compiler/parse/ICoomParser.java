package org.example.coom.compiler.parse;

import org.example.coom.compiler.model.CoomAst;

public interface ICoomParser {

    CoomAst.CoomModel parse(String src, String productName);
}
