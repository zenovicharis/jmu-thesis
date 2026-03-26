package org.example.coom.compiler.profile;

/** Supported normalization profiles. */
public enum NProfile {
    N_LIN,
    N_OUT,
    N_FULL;

    public static NProfile from(String s) {
        if (s == null) return N_LIN;
        return switch (s.trim().toLowerCase()) {
            case "nlin", "n-lin", "lin" -> N_LIN;
            case "nout", "n-out", "out" -> N_OUT;
            case "nfull", "n-full", "full" -> N_FULL;
            default -> N_LIN;
        };
    }
}