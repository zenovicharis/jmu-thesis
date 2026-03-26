package org.example.coom.compiler;

public final class Util {
    private Util() {}

    public static String stripExt(String f) {
        if (f == null) return "";
        int i = f.lastIndexOf('.');
        return (i <= 0) ? f : f.substring(0, i);
    }

    public static String capitalize(String s) {
        return (s == null || s.isEmpty()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static String safeId(String s) {
        if (s == null) return "N";
        String t = s.replaceAll("[^A-Za-z0-9_]", "-");
        if (!t.isEmpty() && !Character.isLetter(t.charAt(0))) t = "N" + t;
        return t;
    }

    public static boolean isInteger(String s) {
        try { Integer.parseInt(s); return true; } catch (Exception e) { return false; }
    }
}
