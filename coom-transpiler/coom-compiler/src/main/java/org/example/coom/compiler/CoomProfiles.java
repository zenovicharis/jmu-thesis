package org.example.coom.compiler;

import org.example.coom.compiler.model.CoomAst;
import org.example.coom.compiler.parse.CoomAntlrParserAdapter;
import org.example.coom.compiler.parse.CoomRegexParser;
import org.example.coom.compiler.parse.ICoomParser;

import java.nio.charset.StandardCharsets;

/**
 * Public API for generating N-profiles (N-LIN / N-OUT / N-FULL) from a COOM file.
 *
 * Intended for supervisor demo:
 * - deterministic outputs
 * - easy diffing
 * - clear separation: parse -> AST -> profile
 */
public final class CoomProfiles {

    public record ProfileResult(String content, String contentType, String filename) { }

    public ProfileResult generate(byte[] fileContent, String fileName, String profileParam, boolean useAntlr) {
        String src = new String(fileContent, StandardCharsets.UTF_8);
        String base = Util.stripExt(fileName);
        String productName = base.isBlank() ? "Product" : base;

        ICoomParser parser = useAntlr ? new CoomAntlrParserAdapter() : new CoomRegexParser();
        CoomAst.CoomModel ast = parser.parse(src, productName);

        // Use the enum defined inside NProfileGenerator (single source of truth)
        NProfileGenerator.NProfile profile = NProfileGenerator.NProfile.from(profileParam);

        String text = new NProfileGenerator().generate(ast, profile);

        String outName = (base.isBlank() ? "model" : base) + "." + switch (profile) {
            case N_LIN -> "nlin.txt";
            case N_OUT -> "nout.txt";
            case N_FULL -> "nfull.txt";
        };

        return new ProfileResult(text, "text/plain", outName);
    }
}
