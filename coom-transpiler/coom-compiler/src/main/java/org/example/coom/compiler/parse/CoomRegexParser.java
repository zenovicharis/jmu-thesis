package org.example.coom.compiler.parse;

import org.example.coom.compiler.model.CoomAst;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.example.coom.compiler.Util.isInteger;

/**
 * Pragmatic line-based COOM parser that supports:
 * - product, enumeration, structure blocks
 * - behavior rules: explanation, require, imply, condition, combinations/allow
 */
public final class CoomRegexParser implements ICoomParser {

    public CoomAst.CoomModel parse(String src, String productName) {
        List<String> lines = preprocess(src);
        CoomAst.CoomModel model = new CoomAst.CoomModel(productName);
        Set<String> enumNames = new HashSet<>();
        Set<String> structNames = new HashSet<>();

        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("enumeration")) {
                String name = captureFirst(line, "^enumeration\\s+(\\w+)");
                if (name != null) enumNames.add(name);
            } else if (line.startsWith("structure")) {
                String name = captureFirst(line, "^structure\\s+(\\w+)");
                if (name != null) structNames.add(name);
            }
        }

        Deque<CoomAst.Block> stack = new ArrayDeque<>();
        String pendingExplanation = null;
        String pendingCondition = null;
        CoomAst.CombinationsBuilder combBuilder = null;
        String currentBehaviorScope = null;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            // openers
            if (line.startsWith("product")) {
                stack.push(CoomAst.Block.product());
                continue;
            }
            if (line.startsWith("enumeration")) {
                String name = captureFirst(line, "^enumeration\\s+(\\w+)");
                if (name != null) stack.push(CoomAst.Block.enumeration(name));
                continue;
            }
            if (line.startsWith("structure")) {
                String name = captureFirst(line, "^structure\\s+(\\w+)");
                if (name != null) stack.push(CoomAst.Block.structure(name));
                continue;
            }
            if (line.startsWith("behavior")) {
                stack.push(CoomAst.Block.behavior());
                pendingExplanation = null;
                pendingCondition = null;
                combBuilder = null;
                currentBehaviorScope = captureFirst(line, "^behavior\\s+(\\w+)");
                continue;
            }

            // closers
            if (line.equals("}")) {
                CoomAst.Block b = stack.pop();
                if (b.kind == CoomAst.Kind.BEHAVIOR && combBuilder != null) {
                    CoomAst.Constraint c = combBuilder.toConstraint(pendingExplanation);
                    c.condition = pendingCondition;
                    c.scopeType = currentBehaviorScope;
                    model.constraints.add(c);
                    combBuilder = null;
                }
                if (b.kind == CoomAst.Kind.BEHAVIOR) {
                    currentBehaviorScope = null;
                    pendingCondition = null;
                }
                continue;
            }

            // in-block parsing
            if (stack.isEmpty()) continue;
            CoomAst.Block cur = stack.peek();

            switch (cur.kind) {
                case PRODUCT -> parseProductLine(line, model.product, model, enumNames, structNames);
                case ENUMERATION -> parseEnumerationLine(line, model.enumerations.computeIfAbsent(cur.name, CoomAst.EnumerationDef::new));
                case STRUCTURE -> parseStructureLine(line, model.structures.computeIfAbsent(cur.name, CoomAst.StructureDef::new), enumNames, structNames);
                case BEHAVIOR -> {
                    if (line.startsWith("explanation")) {
                        String s = captureFirst(line, "^explanation\\s+\"(.*)\"$");
                        pendingExplanation = s;
                        continue;
                    }
                    if (line.startsWith("require")) {
                        String expr = line.substring("require".length()).trim();
                        CoomAst.Constraint c = CoomAst.Constraint.require(expr, pendingExplanation);
                        c.condition = pendingCondition;
                        c.scopeType = currentBehaviorScope;
                        model.constraints.add(c);
                        pendingExplanation = null;
                        pendingCondition = null;
                        combBuilder = null;
                        continue;
                    }
                    if (line.startsWith("imply")) {
                        String expr = line.substring("imply".length()).trim();
                        CoomAst.Constraint c = CoomAst.Constraint.imply(expr, pendingExplanation);
                        c.condition = pendingCondition;
                        c.scopeType = currentBehaviorScope;
                        model.constraints.add(c);
                        pendingExplanation = null;
                        pendingCondition = null;
                        combBuilder = null;
                        continue;
                    }
                    if (line.startsWith("default")) {
                        String expr = line.substring("default".length()).trim();
                        CoomAst.Constraint c = CoomAst.Constraint.defaultAssign(expr, pendingExplanation);
                        c.condition = pendingCondition;
                        c.scopeType = currentBehaviorScope;
                        model.constraints.add(c);
                        pendingExplanation = null;
                        pendingCondition = null;
                        combBuilder = null;
                        continue;
                    }
                    if (line.startsWith("condition")) {
                        String cond = line.substring("condition".length()).trim();
                        pendingCondition = cond;
                        combBuilder = null;
                        continue;
                    }
                    if (line.startsWith("combinations")) {
                        String inside = captureFirst(line, "^combinations\\s*\\(([^)]+)\\)");
                        if (inside != null) {
                            combBuilder = new CoomAst.CombinationsBuilder();
                            for (String v : inside.split("[,\\s]+")) {
                                if (!v.isBlank()) combBuilder.variables.add(v.trim());
                            }
                            combBuilder.explanation = pendingExplanation;
                            pendingExplanation = null;
                        }
                        continue;
                    }
                    if (line.startsWith("allow")) {
                        if (combBuilder == null) continue;
                        String tuple = captureFirst(line, "^allow\\s*\\(([^)]+)\\)");
                        if (tuple != null) {
                            List<String> values = new ArrayList<>();
                            for (String v : tuple.split("[,\\s]+")) {
                                if (!v.isBlank()) values.add(v.trim());
                            }
                            combBuilder.allowed.add(values);
                        }
                    }
                }
            }
        }

        return model;
    }

    private void parseProductLine(String line, CoomAst.ProductDef product, CoomAst.CoomModel model, Set<String> enumNames, Set<String> structNames) {
        // num 0-20 length
        Matcher mNum = Pattern.compile("^num(?:\\.[#0-9]+)?(?:/\\w+)?\\s+(\\d+)-(\\d+)\\s+(\\w+)$").matcher(line);
        if (mNum.find()) {
            product.numAttrs.add(new CoomAst.NumAttr(mNum.group(3), Integer.parseInt(mNum.group(1)), Integer.parseInt(mNum.group(2)), null, null));
            return;
        }
        Matcher mNumSimple = Pattern.compile("^num(?:\\.[#0-9]+)?(?:/\\w+)?\\s+(\\w+)$").matcher(line);
        if (mNumSimple.find()) {
            product.numAttrs.add(new CoomAst.NumAttr(mNumSimple.group(1), Integer.MIN_VALUE, Integer.MAX_VALUE, null, null));
            return;
        }
        Matcher mBool = Pattern.compile("^bool\\s+(\\w+)$").matcher(line);
        if (mBool.find()) {
            product.primitiveAttrs.add(new CoomAst.PrimitiveAttr(mBool.group(1), CoomAst.PrimitiveType.BOOL));
            return;
        }
        Matcher mStr = Pattern.compile("^string\\s+(\\w+)$").matcher(line);
        if (mStr.find()) {
            product.primitiveAttrs.add(new CoomAst.PrimitiveAttr(mStr.group(1), CoomAst.PrimitiveType.STRING));
            return;
        }
        // multiplicity structure: 0..1 Lid lid
        Matcher mMult = Pattern.compile("^(\\d+)\\.\\.(\\d+|\\*)\\s+(\\w+)\\s+(\\w+)$").matcher(line);
        if (mMult.find()) {
            int min = Integer.parseInt(mMult.group(1));
            int max = mMult.group(2).equals("*") ? Integer.MAX_VALUE : Integer.parseInt(mMult.group(2));
            String type = mMult.group(3);
            String name = mMult.group(4);
            product.structRefs.add(new CoomAst.StructRef(name, type, min, max));
            return;
        }
        // enum attribute: Color color  OR Material material
        for (String enumName : enumNames) {
            Matcher mEnum = Pattern.compile("^" + enumName + "\\s+(\\w+)$").matcher(line);
            if (mEnum.find()) {
                product.enumAttrs.add(new CoomAst.EnumAttr(mEnum.group(1), enumName));
                return;
            }
        }
        // Enumeration may appear after product: Capitalized type + name
        Matcher mGenericEnum = Pattern.compile("^(\\w+)\\s+(\\w+)$").matcher(line);
        if (mGenericEnum.find()) {
            String type = mGenericEnum.group(1);
            String name = mGenericEnum.group(2);
            if (structNames.contains(type)) {
                product.structRefs.add(new CoomAst.StructRef(name, type, 1, 1));
            } else if (enumNames.contains(type)) {
                product.enumAttrs.add(new CoomAst.EnumAttr(name, type));
            } else if (!type.isEmpty() && Character.isUpperCase(type.charAt(0))) {
                product.structRefs.add(new CoomAst.StructRef(name, type, 1, 1));
            }
        }
    }

    private void parseEnumerationLine(String line, CoomAst.EnumerationDef ed) {
        // attribute num thickness
        Matcher mAttr = Pattern.compile("^attribute\\s+num\\s+(\\w+)$").matcher(line);
        if (mAttr.find()) {
            ed.enumDataAttrs.add(mAttr.group(1));
            return;
        }
        // Value = ( ... )
        Matcher mVal = Pattern.compile("^(\\w+)\\s*=\\s*\\(([^)]*)\\)\\s*$").matcher(line);
        if (mVal.find()) {
            String name = mVal.group(1);
            String inside = mVal.group(2).trim();
            Map<String,String> vals = new LinkedHashMap<>();
            String[] parts = inside.split("\\s*,\\s*|\\s+");
            for (int i = 0; i < parts.length && i < ed.enumDataAttrs.size(); i++) {
                vals.put(ed.enumDataAttrs.get(i), parts[i]);
            }
            ed.valuesWithAttrs.add(new CoomAst.EnumValueWithAttrs(name, vals));
            return;
        }
        // Bare value
        Matcher mBare = Pattern.compile("^(\\w+)\\s*$").matcher(line);
        if (mBare.find()) {
            ed.valuesNoAttrs.add(mBare.group(1));
        }
    }

    private void parseStructureLine(String line, CoomAst.StructureDef sd, Set<String> enumNames, Set<String> structNames) {
        // num 0-20 length
        Matcher mNum = Pattern.compile("^num(?:\\.[#0-9]+)?(?:/\\w+)?\\s+(\\d+)-(\\d+)\\s+(\\w+)$").matcher(line);
        if (mNum.find()) {
            sd.numAttrs.add(new CoomAst.NumAttr(mNum.group(3), Integer.parseInt(mNum.group(1)), Integer.parseInt(mNum.group(2)), null, null));
            return;
        }
        Matcher mNumSimple = Pattern.compile("^num(?:\\.[#0-9]+)?(?:/\\w+)?\\s+(\\w+)$").matcher(line);
        if (mNumSimple.find()) {
            sd.numAttrs.add(new CoomAst.NumAttr(mNumSimple.group(1), Integer.MIN_VALUE, Integer.MAX_VALUE, null, null));
            return;
        }
        Matcher mBool = Pattern.compile("^bool\\s+(\\w+)$").matcher(line);
        if (mBool.find()) {
            sd.primitiveAttrs.add(new CoomAst.PrimitiveAttr(mBool.group(1), CoomAst.PrimitiveType.BOOL));
            return;
        }
        Matcher mStr = Pattern.compile("^string\\s+(\\w+)$").matcher(line);
        if (mStr.find()) {
            sd.primitiveAttrs.add(new CoomAst.PrimitiveAttr(mStr.group(1), CoomAst.PrimitiveType.STRING));
            return;
        }
        // enum attribute e.g., Material material
        Matcher mGenericEnum = Pattern.compile("^(\\w+)\\s+(\\w+)$").matcher(line);
        if (mGenericEnum.find()) {
            String type = mGenericEnum.group(1);
            String name = mGenericEnum.group(2);
            if (enumNames.contains(type)) {
                sd.enumAttrs.add(new CoomAst.EnumAttr(name, type));
            } else if (structNames.contains(type)) {
                sd.structRefs.add(new CoomAst.StructRef(name, type, 1, 1));
            } else if (!type.isEmpty() && Character.isUpperCase(type.charAt(0))) {
                sd.enumAttrs.add(new CoomAst.EnumAttr(name, type));
            }
        }
    }

    // ---------- helpers ----------

    private static List<String> preprocess(String src) {
        String[] rawLines = src.replace("\r", "").split("\n");
        List<String> out = new ArrayList<>();
        for (String l : rawLines) {
            String noComment = l.replaceAll("//.*$", "").trim();
            if (!noComment.isEmpty()) {
                String tmp = noComment.replace("{", "{\n").replace("}", "\n}");
                for (String piece : tmp.split("\n")) {
                    String s = piece.trim();
                    if (!s.isEmpty()) out.add(s);
                }
            }
        }
        return out;
    }

    private static String captureFirst(String line, String regex) {
        Matcher m = Pattern.compile(regex).matcher(line);
        return m.find() ? m.group(1) : null;
    }

}
