package org.example.coom.compiler.diagnostic;

public record Diagnostic(Severity severity, Stage stage, String message) {
    public static Diagnostic info(Stage stage, String message) {
        return new Diagnostic(Severity.INFO, stage, message);
    }

    public static Diagnostic warning(Stage stage, String message) {
        return new Diagnostic(Severity.WARNING, stage, message);
    }

    public static Diagnostic error(Stage stage, String message) {
        return new Diagnostic(Severity.ERROR, stage, message);
    }
}
