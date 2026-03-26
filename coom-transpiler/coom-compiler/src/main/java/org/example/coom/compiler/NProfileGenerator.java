package org.example.coom.compiler;

import org.example.coom.compiler.model.CoomAst;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates N-profiles from the parsed COOM AST.
 *
 * IMPORTANT:
 * This implementation is schema-tolerant:
 * - It does NOT assume specific field names in your CoomAst records/classes.
 * - It uses reflection to read common variants (e.g., enumType vs enumName).
 *
 * This makes it ideal for a supervisor demo (tomorrow) while your AST is still evolving.
 */
public final class NProfileGenerator {

    public enum NProfile {
        N_LIN, N_OUT, N_FULL;

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

    public String generate(CoomAst.CoomModel model, NProfile profile) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(profile, "profile");
        return switch (profile) {
            case N_LIN -> nlin(model);
            case N_OUT -> nout(model);
            case N_FULL -> nfull(model);
        };
    }

    // ------------------------------------------------------------
    // N-LIN: canonical, sorted fact lines
    // ------------------------------------------------------------

    private String nlin(CoomAst.CoomModel m) {
        List<String> lines = new ArrayList<>();

        String productName = asString(firstNonNull(
                getAny(m, "productName", "name", "modelName"),
                "Product"
        ));
        lines.add("MODEL " + safe(productName));

        Object product = getAny(m, "product", "productDef");
        if (product != null) {
            emitProductFacts(lines, product);
        }

        Map<?, ?> structures = asMap(getAny(m, "structures", "structureDefs"));
        if (structures != null) {
            for (var e : new TreeMap<>(stringKeyMap(structures)).entrySet()) {
                emitStructureFacts(lines, e.getKey(), e.getValue());
            }
        }

        Map<?, ?> enums = asMap(getAny(m, "enumerations", "enums", "enumDefs"));
        if (enums != null) {
            for (var e : new TreeMap<>(stringKeyMap(enums)).entrySet()) {
                emitEnumerationFacts(lines, e.getKey(), e.getValue());
            }
        }

        List<?> constraints = asList(getAny(m, "constraints", "rules"));
        if (constraints != null) {
            for (Object c : constraints) {
                lines.addAll(emitConstraintFacts(c));
            }
        }

        // Canonical sort (keep MODEL first)
        String head = lines.isEmpty() ? "MODEL " + safe(productName) : lines.get(0);
        List<String> rest = lines.size() <= 1 ? List.of() : new ArrayList<>(lines.subList(1, lines.size()));
        rest.sort(String::compareTo);

        return head + "\n" + String.join("\n", rest) + "\n";
    }

    private void emitProductFacts(List<String> out, Object product) {
        // numeric attrs
        for (Object a : safeList(asList(getAny(product, "numAttrs", "numericAttrs", "nums")))) {
            String name = asString(getAny(a, "name", "attr", "id"));
            Integer min = asInt(getAny(a, "min", "lower", "from"));
            Integer max = asInt(getAny(a, "max", "upper", "to"));
            if (name != null) {
                if (min != null && max != null) {
                    out.add("PRODUCT NUM " + safe(name) + " RANGE " + min + ".." + max);
                } else {
                    out.add("PRODUCT NUM " + safe(name));
                }
            }
        }

        // enum attrs
        for (Object a : safeList(asList(getAny(product, "enumAttrs", "enumAttributes", "enums")))) {
            String name = asString(getAny(a, "name", "attr", "id"));
            String enumType = asString(getAny(a, "enumType", "enumName", "type", "typeName"));
            if (name != null) {
                out.add("PRODUCT ENUMATTR " + safe(name) + (enumType != null ? " TYPE " + safe(enumType) : ""));
            }
        }

        // primitive attrs
        for (Object a : safeList(asList(getAny(product, "primitiveAttrs", "primitiveAttributes", "primitives")))) {
            String name = asString(getAny(a, "name", "attr", "id"));
            String type = asString(getAny(a, "type", "primitiveType", "kind"));
            if (name != null) {
                out.add("PRODUCT PRIMATTR " + safe(name) + (type != null ? " TYPE " + safe(type) : ""));
            }
        }

        // structure refs (multiplicity)
        for (Object r : safeList(asList(getAny(product, "structRefs", "structureRefs", "parts")))) {
            String partName = asString(getAny(r, "name", "partName", "id"));
            String type = asString(getAny(r, "type", "typeName", "structureType", "refType"));
            Integer min = asInt(getAny(r, "min", "lower", "minCardinality", "minOccurs"));
            Object maxObj = getAny(r, "max", "upper", "maxCardinality", "maxOccurs");
            String maxS = maxObj == null ? null : String.valueOf(maxObj);

            String mult = "";
            if (min != null || maxS != null) {
                mult = " MULT " + (min == null ? "?" : min) + ".." + (maxS == null ? "?" : maxS);
            }

            if (partName != null) {
                out.add("PRODUCT PART " + safe(partName)
                        + (type != null ? " TYPE " + safe(type) : "")
                        + mult);
            }
        }
    }

    private void emitStructureFacts(List<String> out, String structureName, Object sd) {
        out.add("STRUCT " + safe(structureName));

        for (Object a : safeList(asList(getAny(sd, "numAttrs", "numericAttrs", "nums")))) {
            String name = asString(getAny(a, "name", "attr", "id"));
            Integer min = asInt(getAny(a, "min", "lower", "from"));
            Integer max = asInt(getAny(a, "max", "upper", "to"));
            if (name != null) {
                if (min != null && max != null) {
                    out.add("STRUCT " + safe(structureName) + " NUM " + safe(name) + " RANGE " + min + ".." + max);
                } else {
                    out.add("STRUCT " + safe(structureName) + " NUM " + safe(name));
                }
            }
        }

        for (Object a : safeList(asList(getAny(sd, "enumAttrs", "enumAttributes", "enums")))) {
            String name = asString(getAny(a, "name", "attr", "id"));
            String enumType = asString(getAny(a, "enumType", "enumName", "type", "typeName"));
            if (name != null) {
                out.add("STRUCT " + safe(structureName) + " ENUMATTR " + safe(name)
                        + (enumType != null ? " TYPE " + safe(enumType) : ""));
            }
        }

        for (Object a : safeList(asList(getAny(sd, "primitiveAttrs", "primitiveAttributes", "primitives")))) {
            String name = asString(getAny(a, "name", "attr", "id"));
            String type = asString(getAny(a, "type", "primitiveType", "kind"));
            if (name != null) {
                out.add("STRUCT " + safe(structureName) + " PRIMATTR " + safe(name)
                        + (type != null ? " TYPE " + safe(type) : ""));
            }
        }

        for (Object r : safeList(asList(getAny(sd, "structRefs", "structureRefs", "parts")))) {
            String partName = asString(getAny(r, "name", "partName", "id"));
            String type = asString(getAny(r, "type", "typeName", "structureType", "refType"));
            Integer min = asInt(getAny(r, "min", "lower", "minCardinality", "minOccurs"));
            Object maxObj = getAny(r, "max", "upper", "maxCardinality", "maxOccurs");
            String maxS = maxObj == null ? null : String.valueOf(maxObj);

            String mult = "";
            if (min != null || maxS != null) {
                mult = " MULT " + (min == null ? "?" : min) + ".." + (maxS == null ? "?" : maxS);
            }

            if (partName != null) {
                out.add("STRUCT " + safe(structureName) + " PART " + safe(partName)
                        + (type != null ? " TYPE " + safe(type) : "")
                        + mult);
            }
        }
    }

    private void emitEnumerationFacts(List<String> out, String enumName, Object ed) {
        out.add("ENUM " + safe(enumName));

        for (Object da : safeList(asList(getAny(ed, "enumDataAttrs", "dataAttrs", "attributes")))) {
            String attr = asString(da);
            if (attr != null) out.add("ENUM " + safe(enumName) + " DATAATTR " + safe(attr));
        }

        for (Object v : safeList(asList(getAny(ed, "valuesNoAttrs", "values", "bareValues")))) {
            String vv = asString(getAny(v, "name", "id"));
            if (vv == null) vv = asString(v);
            if (vv != null) out.add("ENUM " + safe(enumName) + " VALUE " + safe(vv));
        }

        for (Object vw : safeList(asList(getAny(ed, "valuesWithAttrs", "valuesWithData", "tuples")))) {
            String name = asString(getAny(vw, "name", "id", "value"));
            if (name == null) name = asString(vw);

            Object attrsObj = getAny(vw, "attrs", "values", "data", "map", "properties");
            Map<?, ?> attrs = asMap(attrsObj);

            if (name != null) {
                if (attrs != null && !attrs.isEmpty()) {
                    String attrsS = attrs.entrySet().stream()
                            .map(kv -> safe(String.valueOf(kv.getKey())) + "=" + String.valueOf(kv.getValue()))
                            .collect(Collectors.joining(","));
                    out.add("ENUM " + safe(enumName) + " VALUE " + safe(name) + " {" + attrsS + "}");
                } else {
                    out.add("ENUM " + safe(enumName) + " VALUE " + safe(name));
                }
            }
        }
    }

    private List<String> emitConstraintFacts(Object c) {
        List<String> out = new ArrayList<>();

        String kind = asString(getAny(c, "kind", "type", "ruleKind"));
        if (kind == null) kind = "RULE";

        String expr = asString(getAny(c, "expr", "expression", "text", "body"));
        String cond = asString(getAny(c, "condition", "when", "ifExpr"));
        String expl = asString(getAny(c, "explanation", "msg", "message"));
        String scope = asString(getAny(c, "scopeType", "scope", "ownerType"));

        String base = "CONSTRAINT " + safe(kind) + (expr != null ? " " + expr.trim() : "");
        out.add(base);

        if (scope != null && !scope.isBlank()) {
            out.add("CONSTRAINT " + safe(kind) + " SCOPE " + safe(scope));
        }
        if (cond != null && !cond.isBlank()) {
            out.add("CONSTRAINT " + safe(kind) + " IF " + cond.trim());
        }
        if (expl != null && !expl.isBlank()) {
            out.add("CONSTRAINT " + safe(kind) + " EXPL \"" + expl.replace("\"", "\\\"") + "\"");
        }

        // combinations-like optional payload
        List<?> allowed = asList(getAny(c, "allowed", "tuples", "allowedTuples", "allowedCombinations"));
        if (allowed != null && !allowed.isEmpty()) {
            for (Object tup : allowed) {
                out.add("CONSTRAINT " + safe(kind) + " ALLOW " + String.valueOf(tup));
            }
        }

        return out;
    }

    // ------------------------------------------------------------
    // N-OUT: variables/domains/constraints view
    // ------------------------------------------------------------

    private String nout(CoomAst.CoomModel m) {
        StringBuilder sb = new StringBuilder();

        String productName = asString(firstNonNull(
                getAny(m, "productName", "name", "modelName"),
                "Product"
        ));
        sb.append("model: ").append(productName).append("\n");

        Object product = getAny(m, "product", "productDef");

        Map<String, String> variables = new TreeMap<>();

        if (product != null) {
            for (Object a : safeList(asList(getAny(product, "numAttrs", "numericAttrs", "nums")))) {
                String name = asString(getAny(a, "name", "attr", "id"));
                Integer min = asInt(getAny(a, "min", "lower", "from"));
                Integer max = asInt(getAny(a, "max", "upper", "to"));
                if (name != null) {
                    variables.put(name, (min != null && max != null) ? "Num[" + min + ".." + max + "]" : "Num");
                }
            }
            for (Object a : safeList(asList(getAny(product, "enumAttrs", "enumAttributes", "enums")))) {
                String name = asString(getAny(a, "name", "attr", "id"));
                String enumType = asString(getAny(a, "enumType", "enumName", "type", "typeName"));
                if (name != null) variables.put(name, enumType != null ? "Enum(" + enumType + ")" : "Enum");
            }
            for (Object a : safeList(asList(getAny(product, "primitiveAttrs", "primitiveAttributes", "primitives")))) {
                String name = asString(getAny(a, "name", "attr", "id"));
                String type = asString(getAny(a, "type", "primitiveType", "kind"));
                if (name != null) variables.put(name, type != null ? "Prim(" + type + ")" : "Prim");
            }
            for (Object r : safeList(asList(getAny(product, "structRefs", "structureRefs", "parts")))) {
                String partName = asString(getAny(r, "name", "partName", "id"));
                String type = asString(getAny(r, "type", "typeName", "structureType", "refType"));
                Object minObj = getAny(r, "min", "lower", "minCardinality", "minOccurs");
                Object maxObj = getAny(r, "max", "upper", "maxCardinality", "maxOccurs");
                String mult = (minObj != null || maxObj != null)
                        ? "[" + (minObj == null ? "?" : minObj) + ".." + (maxObj == null ? "?" : maxObj) + "]"
                        : "";
                if (partName != null) variables.put(partName, "Part(" + (type != null ? type : "?") + ")" + mult);
            }
        }

        sb.append("variables:\n");
        if (variables.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (var e : variables.entrySet()) {
                sb.append("  - ").append(e.getKey()).append(" : ").append(e.getValue()).append("\n");
            }
        }

        Map<?, ?> enums = asMap(getAny(m, "enumerations", "enums", "enumDefs"));
        sb.append("domains:\n");
        if (enums == null || enums.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (var e : new TreeMap<>(stringKeyMap(enums)).entrySet()) {
                String enumName = e.getKey();
                Object ed = e.getValue();

                List<String> vals = new ArrayList<>();

                for (Object v : safeList(asList(getAny(ed, "valuesNoAttrs", "values", "bareValues")))) {
                    String vv = asString(getAny(v, "name", "id"));
                    if (vv == null) vv = asString(v);
                    if (vv != null) vals.add(vv);
                }
                for (Object vw : safeList(asList(getAny(ed, "valuesWithAttrs", "valuesWithData", "tuples")))) {
                    String vv = asString(getAny(vw, "name", "id", "value"));
                    if (vv == null) vv = asString(vw);
                    if (vv != null) vals.add(vv);
                }

                vals = vals.stream().distinct().sorted().toList();

                sb.append("  - ").append(enumName).append(": ").append(vals).append("\n");
            }
        }

        List<?> constraints = asList(getAny(m, "constraints", "rules"));
        sb.append("constraints:\n");
        if (constraints == null || constraints.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            int i = 0;
            for (Object c : constraints) {
                i++;
                String kind = asString(getAny(c, "kind", "type", "ruleKind"));
                if (kind == null) kind = "RULE";
                String expr = asString(getAny(c, "expr", "expression", "text", "body"));
                String cond = asString(getAny(c, "condition", "when", "ifExpr"));
                String expl = asString(getAny(c, "explanation", "msg", "message"));

                sb.append("  - #").append(i).append(" kind: ").append(kind).append("\n");
                if (cond != null && !cond.isBlank()) sb.append("    if: ").append(cond.trim()).append("\n");
                if (expl != null && !expl.isBlank()) sb.append("    explanation: ").append(expl.trim()).append("\n");
                if (expr != null && !expr.isBlank()) sb.append("    expr: ").append(expr.trim()).append("\n");

                List<?> allowed = asList(getAny(c, "allowed", "tuples", "allowedTuples", "allowedCombinations"));
                if (allowed != null && !allowed.isEmpty()) {
                    sb.append("    allowed:\n");
                    for (Object tup : allowed) {
                        sb.append("      - ").append(String.valueOf(tup)).append("\n");
                    }
                }
            }
        }

        return sb.toString();
    }

    // ------------------------------------------------------------
    // N-FULL: expanded facts (materialize enum value tuples)
    // ------------------------------------------------------------

    private String nfull(CoomAst.CoomModel m) {
        List<String> lines = new ArrayList<>();

        String productName = asString(firstNonNull(
                getAny(m, "productName", "name", "modelName"),
                "Product"
        ));
        lines.add("MODEL " + safe(productName));

        Object product = getAny(m, "product", "productDef");
        if (product != null) emitProductFacts(lines, product);

        Map<?, ?> structures = asMap(getAny(m, "structures", "structureDefs"));
        if (structures != null) {
            for (var e : new TreeMap<>(stringKeyMap(structures)).entrySet()) {
                emitStructureFacts(lines, e.getKey(), e.getValue());
            }
        }

        Map<?, ?> enums = asMap(getAny(m, "enumerations", "enums", "enumDefs"));
        if (enums != null) {
            for (var e : new TreeMap<>(stringKeyMap(enums)).entrySet()) {
                String enumName = e.getKey();
                Object ed = e.getValue();

                lines.add("ENUM " + safe(enumName));

                for (Object da : safeList(asList(getAny(ed, "enumDataAttrs", "dataAttrs", "attributes")))) {
                    String attr = asString(da);
                    if (attr != null) lines.add("ENUM " + safe(enumName) + " DATAATTR " + safe(attr));
                }

                for (Object vw : safeList(asList(getAny(ed, "valuesWithAttrs", "valuesWithData", "tuples")))) {
                    String vName = asString(getAny(vw, "name", "id", "value"));
                    if (vName == null) vName = asString(vw);
                    if (vName == null) continue;

                    lines.add("ENUMVALUE " + safe(enumName) + "." + safe(vName));

                    Map<?, ?> attrs = asMap(getAny(vw, "attrs", "values", "data", "map", "properties"));
                    if (attrs != null) {
                        for (var kv : attrs.entrySet()) {
                            lines.add("ENUMVALUE " + safe(enumName) + "." + safe(vName)
                                    + " ATTR " + safe(String.valueOf(kv.getKey()))
                                    + " = " + String.valueOf(kv.getValue()));
                        }
                    }
                }

                for (Object v : safeList(asList(getAny(ed, "valuesNoAttrs", "values", "bareValues")))) {
                    String vName = asString(getAny(v, "name", "id"));
                    if (vName == null) vName = asString(v);
                    if (vName != null) lines.add("ENUMVALUE " + safe(enumName) + "." + safe(vName));
                }
            }
        }

        List<?> constraints = asList(getAny(m, "constraints", "rules"));
        if (constraints != null) {
            for (Object c : constraints) lines.addAll(emitConstraintFacts(c));
        }

        // Sort deterministically (keep MODEL first)
        String head = lines.get(0);
        List<String> rest = new ArrayList<>(lines.subList(1, lines.size()));
        rest.sort(String::compareTo);

        return head + "\n" + String.join("\n", rest) + "\n";
    }

    // ------------------------------------------------------------
    // Reflection helpers (schema tolerant)
    // ------------------------------------------------------------

    private static Object getAny(Object obj, String... names) {
        if (obj == null) return null;

        // 1) Try method accessors: name(), getName()
        for (String n : names) {
            Object v = tryCallNoArg(obj, n);
            if (v != null) return v;
            v = tryCallNoArg(obj, "get" + capitalize(n));
            if (v != null) return v;
        }

        // 2) Try public/protected/private fields
        for (String n : names) {
            Object v = tryGetField(obj, n);
            if (v != null) return v;
        }

        return null;
    }

    private static Object tryCallNoArg(Object obj, String methodName) {
        try {
            Method m = obj.getClass().getMethod(methodName);
            return m.invoke(obj);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object tryGetField(Object obj, String fieldName) {
        Class<?> c = obj.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.get(obj);
            } catch (Exception ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static Map<?, ?> asMap(Object o) {
        return (o instanceof Map<?, ?> m) ? m : null;
    }

    private static List<?> asList(Object o) {
        return (o instanceof List<?> l) ? l : null;
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> m) {
        Map<String, Object> out = new HashMap<>();
        for (var e : m.entrySet()) {
            if (e.getKey() != null) out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    private static List<Object> safeList(List<?> xs) {
        if (xs == null) return List.of();
        List<Object> out = new ArrayList<>(xs.size());
        for (Object x : xs) out.add(x);
        return out;
    }

    private static String asString(Object o) {
        if (o == null) return null;
        if (o instanceof String s) return s;
        return String.valueOf(o);
    }

    private static Integer asInt(Object o) {
        if (o == null) return null;
        if (o instanceof Integer i) return i;
        if (o instanceof Long l) return (int) (long) l;
        if (o instanceof Short s) return (int) (short) s;
        if (o instanceof String str) {
            try { return Integer.parseInt(str.trim()); } catch (Exception ignored) {}
        }
        return null;
    }

    private static String safe(String s) {
        if (s == null) return "null";
        return s.trim().replaceAll("\\s+", " ");
    }
}
