package org.example.coom.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.jena.rdf.model.Model;
import org.example.coom.compiler.diagnostic.Diagnostic;
import org.example.coom.compiler.model.CoomAst;

public final class CompilationContext {
    private final byte[] sourceBytes;
    private final String sourceText;
    private final String fileName;
    private final String productName;
    private final CompilationOptions options;

    private ParseTree parseTree;
    private CoomAst.CoomModel ast;
    private Model rdfModel;
    private String output;
    private String outputFilename;
    private String outputContentType;
    private boolean conforms = true;
    private String validationReport;

    private final Map<String, Object> artifacts = new HashMap<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    public CompilationContext(byte[] sourceBytes, String sourceText, String fileName, String productName, CompilationOptions options) {
        this.sourceBytes = sourceBytes;
        this.sourceText = sourceText;
        this.fileName = fileName;
        this.productName = productName;
        this.options = options;
    }

    public byte[] sourceBytes() {
        return sourceBytes;
    }

    public String sourceText() {
        return sourceText;
    }

    public String fileName() {
        return fileName;
    }

    public String productName() {
        return productName;
    }

    public CompilationOptions options() {
        return options;
    }

    public ParseTree parseTree() {
        return parseTree;
    }

    public void setParseTree(ParseTree parseTree) {
        this.parseTree = parseTree;
    }

    public CoomAst.CoomModel ast() {
        return ast;
    }

    public void setAst(CoomAst.CoomModel ast) {
        this.ast = ast;
    }

    public Model rdfModel() {
        return rdfModel;
    }

    public void setRdfModel(Model rdfModel) {
        this.rdfModel = rdfModel;
    }

    public String output() {
        return output;
    }

    public String outputFilename() {
        return outputFilename;
    }

    public String outputContentType() {
        return outputContentType;
    }

    public boolean conforms() {
        return conforms;
    }

    public String validationReport() {
        return validationReport;
    }

    public void setOutput(String output, String outputContentType, String outputFilename) {
        this.output = output;
        this.outputContentType = outputContentType;
        this.outputFilename = outputFilename;
    }

    public void setValidationResult(boolean conforms, String report) {
        this.conforms = conforms;
        this.validationReport = report;
    }

    public Map<String, Object> artifacts() {
        return artifacts;
    }

    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    public void addDiagnostic(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    public boolean hasError() {
        return diagnostics.stream().anyMatch(d -> d.severity() == org.example.coom.compiler.diagnostic.Severity.ERROR);
    }
}
