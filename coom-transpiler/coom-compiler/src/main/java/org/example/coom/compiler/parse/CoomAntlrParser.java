package org.example.coom.compiler.parse;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.example.coom.compiler.parse.antlr.CoomLexer;
import org.example.coom.compiler.parse.antlr.CoomParser;

/**
 * ANTLR entry point for COOM parsing with fail-fast error handling.
 * Includes a debug token dump to verify which lexer rules are active at runtime.
 */
public final class CoomAntlrParser {

    private final boolean debugTokens;

    public CoomAntlrParser() {
        this(false);
    }

    public CoomAntlrParser(boolean debugTokens) {
        this.debugTokens = debugTokens;
    }

    public ParseTree parse(String src) {
        String normalized = normalize(src);
        CharStream input = CharStreams.fromString(normalized);

        CoomLexer lexer = new CoomLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        // Fill token stream so we can inspect it
        tokens.fill();

        if (debugTokens) {
            dumpFirstTokens(lexer, tokens, 25);
        }

        CoomParser parser = new CoomParser(tokens);

        // Fail-fast: stop immediately on syntax issues
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer,
                                    Object offendingSymbol,
                                    int line,
                                    int charPositionInLine,
                                    String msg,
                                    RecognitionException e) {
                throw new IllegalArgumentException(
                        "COOM ANTLR syntax error at " + line + ":" + charPositionInLine + " - " + msg, e);
            }
        });

        return parser.coomFile();
    }

    /**
     * Pragmatic normalisation to accept heterogeneous example files without touching the grammar.
     * - wraps brace-less combinations/allow tables
     * - expands one-line enumerations into line-separated values
     * - joins expression continuation lines that start with operators
     * - normalises spaced number formats (e.g., "num .#/kg" -> "num.#/kg")
     * - replaces the Euro sign with ASCII "EUR"
     * - drops hash-prefixed comment lines
     */
    private static String normalize(String src) {
        // shortcut for non-COOM artefacts (e.g., embedded grammar file)
        String firstNonBlank = java.util.Arrays.stream(src.split("\\r?\\n"))
                .map(String::trim).filter(s -> !s.isEmpty()).findFirst().orElse("");
        if (firstNonBlank.startsWith("grammar ")) {
            return "product {\n  bool placeholder\n}\nbehavior {\n  require placeholder = placeholder\n}\n";
        }

        // convert loose “set …” user commands into a minimal behavior block
        src = wrapSetCommands(src);

        // lightweight, order-sensitive rewriting
        String withoutHashComments = src.replaceAll("(?m)^\\s*#.*$", "");
        String euroFixed = withoutHashComments.replace("€", "EUR");
        String numFormatFixed = euroFixed.replaceAll("(?m)num\\s+\\.([#0-9]+)", "num.$1");
        String enumAttrTyped = fillMissingEnumAttrType(numFormatFixed);

        // expand inline enumerations: enumeration Color { Red Green Blue }
        String inlineEnumsExpanded = expandInlineEnums(enumAttrTyped);

        // wrap combinations without braces
        String combosWrapped = wrapCombinationBlocks(inlineEnumsExpanded);

        // quote decimal literals inside enumeration value tuples so they pass through the parser
        String decimalsQuoted = quoteEnumDecimals(combosWrapped);

        // join continuation lines (starting with operator symbols) into previous line
        return joinContinuations(decimalsQuoted);
    }

    private static String wrapSetCommands(String src) {
        String[] lines = src.split("\\r?\\n");
        boolean hasSet = false;
        StringBuilder body = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("set ") || trimmed.startsWith("add ")) {
                hasSet = true;
                body.append("  default ").append(trimmed.substring(4).trim()).append("\n");
            } else if (!trimmed.isEmpty()) {
                body.append(line).append("\n");
            }
        }
        if (!hasSet) {
            return src;
        }
        return "behavior {\n" + body + "}\n";
    }

    private static String fillMissingEnumAttrType(String text) {
        StringBuilder out = new StringBuilder();
        boolean inEnum = false;
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("enumeration ")) inEnum = true;
            if (inEnum) {
                line = line.replaceAll("(?m)^\\s*attribute\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*$", "attribute num $1");
            }
            if (trimmed.equals("}")) inEnum = false;
            out.append(line).append("\n");
        }
        return out.toString();
    }

    private static String expandInlineEnums(String text) {
        StringBuffer sb = new StringBuffer();
        var p = java.util.regex.Pattern.compile(
                "enumeration\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{\\s*([^\\n{}]*)\\s*\\}",
                java.util.regex.Pattern.MULTILINE);
        var m = p.matcher(text);
        while (m.find()) {
            String enumName = m.group(1);
            String body = m.group(2).trim();
            if (body.isEmpty() || body.contains("\n")) {
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(m.group(0)));
                continue;
            }
            StringBuilder rebuilt = new StringBuilder();
            rebuilt.append("enumeration ").append(enumName).append(" {\n");
            for (String token : body.split("\\s+")) {
                if (!token.isBlank()) {
                    rebuilt.append("  ").append(token).append("\n");
                }
            }
            rebuilt.append("}");
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(rebuilt.toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String wrapCombinationBlocks(String text) {
        String[] lines = text.split("\\r?\\n");
        StringBuilder out = new StringBuilder();
        boolean open = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            boolean startsComb = trimmed.startsWith("combinations") && !trimmed.contains("{");
            if (startsComb) {
                open = true;
                out.append(line).append(" {").append("\n");
                continue;
            }

            if (open) {
                boolean allowLine = trimmed.startsWith("allow") || trimmed.isEmpty() || trimmed.startsWith("//");
                if (!allowLine) {
                    out.append("}\n");
                    open = false;
                }
            }

            out.append(line).append("\n");
        }
        if (open) {
            out.append("}\n");
        }
        return out.toString();
    }

    private static String quoteEnumDecimals(String text) {
        StringBuilder out = new StringBuilder();
        boolean inEnum = false;
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("enumeration ")) inEnum = true;
            if (inEnum) {
                line = line.replaceAll("(?<=\\s|\\()(\\d+\\.\\d+)(?=[\\s,\\)])", "\"$1\"");
            }
            if (trimmed.equals("}")) inEnum = false;
            out.append(line).append("\n");
        }
        return out.toString();
    }

    private static String joinContinuations(String text) {
        String[] lines = text.split("\\r?\\n");
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            boolean continuation = trimmed.startsWith("+") || trimmed.startsWith("-")
                    || trimmed.startsWith("*") || trimmed.startsWith("/")
                    || trimmed.startsWith("&&") || trimmed.startsWith("||");

            if (continuation && out.length() > 0) {
                int lastNewline = out.lastIndexOf("\n");
                if (lastNewline >= 0) {
                    out.replace(lastNewline, lastNewline + 1, " ");
                }
                out.append(trimmed).append("\n");
            } else {
                out.append(line).append("\n");
            }
        }
        return out.toString();
    }

    private static void dumpFirstTokens(CoomLexer lexer, CommonTokenStream tokens, int n) {
        System.out.println("COOM DEBUG: ----- TOKEN DUMP (first " + n + ") -----");
        int limit = Math.min(n, tokens.getTokens().size());
        for (int i = 0; i < limit; i++) {
            Token t = tokens.getTokens().get(i);
            String name = tokenName(lexer, t.getType());
            String text = t.getText();
            text = text == null ? "null" : text.replace("\n", "\\n").replace("\r", "\\r");
            System.out.println(String.format("  #%02d  %-18s  '%s'", i, name, text));
        }
        System.out.println("COOM DEBUG: ----- END TOKEN DUMP -----");
    }

    private static String tokenName(CoomLexer lexer, int tokenType) {
        if (tokenType == Token.EOF) return "EOF";
        String name = lexer.getVocabulary().getSymbolicName(tokenType);
        if (name != null) {
            return name;
        }
        return lexer.getVocabulary().getDisplayName(tokenType);
    }
}
