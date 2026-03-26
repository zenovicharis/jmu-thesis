package org.example.coom.compiler.parse;

import org.example.coom.compiler.model.CoomAst;
import org.example.coom.compiler.parse.antlr.CoomParser;
import org.example.coom.compiler.parse.antlr.CoomParserBaseVisitor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ANTLR parse-tree -> CoomAst builder.
 * This file assumes the grammar Coom.g4 as provided.
 */
public final class CoomAstBuilder extends CoomParserBaseVisitor<Void> {

    private final CoomAst.CoomModel model;
    private final List<String> declaredEnums = new ArrayList<>();
    private final List<String> declaredStructures = new ArrayList<>();

    // behavior state
    private String pendingExplanation = null;
    private String pendingCondition = null;
    private CoomAst.CombinationsBuilder combBuilder = null;
    private String currentBehaviorScope = null;

    // context to know current enum/structure name while visiting
    private String currentEnumName = null;
    private String currentStructureName = null;

    public CoomAstBuilder(String productName) {
        this.model = new CoomAst.CoomModel(productName);
    }

    public CoomAst.CoomModel getModel() {
        return model;
    }

    @Override
    public Void visitCoomFile(CoomParser.CoomFileContext ctx) {
        for (var stmt : ctx.statement()) {
            if (stmt.enumerationDecl() != null) {
                declaredEnums.add(stmt.enumerationDecl().IDENT().getText());
            } else if (stmt.structureDecl() != null) {
                declaredStructures.add(stmt.structureDecl().IDENT().getText());
            }
        }
        return super.visitCoomFile(ctx);
    }

    @Override
    public Void visitProductDecl(CoomParser.ProductDeclContext ctx) {
        for (var line : ctx.productLine()) {
            visit(line);
        }
        return null;
    }

    @Override
    public Void visitEnumerationDecl(CoomParser.EnumerationDeclContext ctx) {
        currentEnumName = ctx.IDENT().getText();
        model.enumerations.computeIfAbsent(currentEnumName, CoomAst.EnumerationDef::new);

        for (var line : ctx.enumerationLine()) {
            visit(line);
        }
        currentEnumName = null;
        return null;
    }

    @Override
    public Void visitStructureDecl(CoomParser.StructureDeclContext ctx) {
        currentStructureName = ctx.IDENT().getText();
        model.structures.computeIfAbsent(currentStructureName, CoomAst.StructureDef::new);

        for (var line : ctx.structureLine()) {
            visit(line);
        }
        currentStructureName = null;
        return null;
    }

    @Override
    public Void visitBehaviorDecl(CoomParser.BehaviorDeclContext ctx) {
        pendingExplanation = null;
        pendingCondition = null;
        combBuilder = null;
        currentBehaviorScope = ctx.IDENT() != null ? ctx.IDENT().getText() : null;

        for (var stmt : ctx.behaviorStmt()) {
            visit(stmt);
        }

        // end-of-behavior: if combinations was open and not closed by visitor
        if (combBuilder != null) {
            CoomAst.Constraint c = combBuilder.toConstraint(pendingExplanation);
            c.condition = pendingCondition;
            c.scopeType = currentBehaviorScope;
            model.constraints.add(c);
            combBuilder = null;
        }

        currentBehaviorScope = null;
        return null;
    }

    // ---------------- numeric declarations ----------------

    @Override
    public Void visitNumDecl(CoomParser.NumDeclContext ctx) {
        String name = ctx.IDENT().getText();
        String unit = (ctx.unitSpec() != null) ? ctx.unitSpec().IDENT().getText() : null;
        String format = parseNumFormat(ctx.NUMTYPE().getText());

        // Alternative A: "num 0-20 length"
        if (ctx.numRange() != null) {
            int min = Integer.parseInt(ctx.numRange().INT(0).getText());
            int max = Integer.parseInt(ctx.numRange().INT(1).getText());

            if (currentStructureName != null) {
                model.structures.get(currentStructureName)
                        .numAttrs.add(new CoomAst.NumAttr(name, min, max, unit, format));
            } else {
                model.product.numAttrs.add(new CoomAst.NumAttr(name, min, max, unit, format));
            }
            return null;
        }

        // Alternative B: "num /mm reach" OR "num reach"
        // Your current AST requires a min/max. We use a pragmatic sentinel range for now.
        // Recommended future improvement: extend CoomAst.NumAttr to store unit and optional range.
        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;

        if (currentStructureName != null) {
            model.structures.get(currentStructureName)
                    .numAttrs.add(new CoomAst.NumAttr(name, min, max, unit, format));
        } else {
            model.product.numAttrs.add(new CoomAst.NumAttr(name, min, max, unit, format));
        }

        return null;
    }

    @Override
    public Void visitMultiplicityDecl(CoomParser.MultiplicityDeclContext ctx) {
        int min = Integer.parseInt(ctx.INT(0).getText());
        String maxTok = ctx.getChild(2).getText();
        int max = maxTok.equals("*") ? Integer.MAX_VALUE : Integer.parseInt(maxTok);

        String type = ctx.IDENT(0).getText();
        String name = ctx.IDENT(1).getText();

        if (currentStructureName != null) {
            model.structures.get(currentStructureName)
                    .structRefs.add(new CoomAst.StructRef(name, type, min, max));
        } else {
            model.product.structRefs.add(new CoomAst.StructRef(name, type, min, max));
        }
        return null;
    }

    // ---------------- typed attributes ----------------

    @Override
    public Void visitTypedAttrDecl(CoomParser.TypedAttrDeclContext ctx) {
        String typeOrEnum = ctx.IDENT(0).getText();
        String attrName = ctx.IDENT(1).getText();
        boolean isEnumType = declaredEnums.contains(typeOrEnum);
        boolean isStructType = declaredStructures.contains(typeOrEnum);

        if (currentStructureName != null) {
            if (isEnumType) {
                model.structures.get(currentStructureName)
                        .enumAttrs.add(new CoomAst.EnumAttr(attrName, typeOrEnum));
            } else if (isStructType) {
                model.structures.get(currentStructureName)
                        .structRefs.add(new CoomAst.StructRef(attrName, typeOrEnum, 1, 1));
            } else {
                // fallback: treat unknown Capitalized types as enums
                if (!typeOrEnum.isEmpty() && Character.isUpperCase(typeOrEnum.charAt(0))) {
                    model.structures.get(currentStructureName)
                            .enumAttrs.add(new CoomAst.EnumAttr(attrName, typeOrEnum));
                }
            }
        } else {
            if (isStructType) {
                model.product.structRefs.add(new CoomAst.StructRef(attrName, typeOrEnum, 1, 1));
            } else if (isEnumType) {
                model.product.enumAttrs.add(new CoomAst.EnumAttr(attrName, typeOrEnum));
            } else if (!typeOrEnum.isEmpty() && Character.isUpperCase(typeOrEnum.charAt(0))) {
                model.product.structRefs.add(new CoomAst.StructRef(attrName, typeOrEnum, 1, 1));
            }
        }
        return null;
    }

    @Override
    public Void visitPrimitiveAttrDecl(CoomParser.PrimitiveAttrDeclContext ctx) {
        String type = ctx.getChild(0).getText();
        String attrName = ctx.IDENT().getText();
        CoomAst.PrimitiveType prim = "bool".equals(type) ? CoomAst.PrimitiveType.BOOL : CoomAst.PrimitiveType.STRING;

        if (currentStructureName != null) {
            model.structures.get(currentStructureName)
                    .primitiveAttrs.add(new CoomAst.PrimitiveAttr(attrName, prim));
        } else {
            model.product.primitiveAttrs.add(new CoomAst.PrimitiveAttr(attrName, prim));
        }
        return null;
    }

    // ---------------- enumeration lines ----------------

    @Override
    public Void visitEnumAttributeDecl(CoomParser.EnumAttributeDeclContext ctx) {
        var ed = model.enumerations.get(currentEnumName);

        // attribute num /mm length  -> store "length" as data attribute
        ed.enumDataAttrs.add(ctx.IDENT().getText());
        return null;
    }

    @Override
    public Void visitEnumValueWithAttrsDecl(CoomParser.EnumValueWithAttrsDeclContext ctx) {
        var ed = model.enumerations.get(currentEnumName);

        String valueName = ctx.IDENT().getText();
        Map<String, String> vals = new LinkedHashMap<>();

        if (ctx.valueList() != null) {
            var items = ctx.valueList().value();
            for (int i = 0; i < items.size() && i < ed.enumDataAttrs.size(); i++) {
                vals.put(ed.enumDataAttrs.get(i), normalizeValue(items.get(i).getText()));
            }
        }

        ed.valuesWithAttrs.add(new CoomAst.EnumValueWithAttrs(valueName, vals));
        return null;
    }

    @Override
    public Void visitEnumBareValueDecl(CoomParser.EnumBareValueDeclContext ctx) {
        var ed = model.enumerations.get(currentEnumName);
        ed.valuesNoAttrs.add(ctx.IDENT().getText());
        return null;
    }

    // ---------------- behavior ----------------

    @Override
    public Void visitExplanationStmt(CoomParser.ExplanationStmtContext ctx) {
        pendingExplanation = unquote(ctx.STRING().getText());
        return null;
    }

    @Override
    public Void visitDefaultStmt(CoomParser.DefaultStmtContext ctx) {
        String expr = ctx.expr().getText().trim();
        CoomAst.Constraint c = CoomAst.Constraint.defaultAssign(expr, pendingExplanation);
        c.condition = pendingCondition;
        c.scopeType = currentBehaviorScope;
        model.constraints.add(c);
        pendingExplanation = null;
        pendingCondition = null;
        combBuilder = null;
        return null;
    }

    @Override
    public Void visitRequireStmt(CoomParser.RequireStmtContext ctx) {
        String expr = ctx.expr().getText().trim();
        CoomAst.Constraint c = CoomAst.Constraint.require(expr, pendingExplanation);
        c.condition = pendingCondition;
        c.scopeType = currentBehaviorScope;
        model.constraints.add(c);
        pendingExplanation = null;
        pendingCondition = null;
        combBuilder = null;
        return null;
    }

    @Override
    public Void visitImplyStmt(CoomParser.ImplyStmtContext ctx) {
        String expr = ctx.expr().getText().trim();
        CoomAst.Constraint c = CoomAst.Constraint.imply(expr, pendingExplanation);
        c.condition = pendingCondition;
        c.scopeType = currentBehaviorScope;
        model.constraints.add(c);
        pendingExplanation = null;
        pendingCondition = null;
        combBuilder = null;
        return null;
    }

    @Override
    public Void visitConditionStmt(CoomParser.ConditionStmtContext ctx) {
        pendingCondition = ctx.expr().getText().trim();
        combBuilder = null;
        return null;
    }

    @Override
    public Void visitCombinationsBlock(CoomParser.CombinationsBlockContext ctx) {
        combBuilder = new CoomAst.CombinationsBuilder();
        combBuilder.explanation = pendingExplanation;
        pendingExplanation = null;

        if (ctx.identList() != null) {
            for (var p : ctx.identList().path()) {
                combBuilder.variables.add(p.getText());
            }
        }

        for (var allow : ctx.allowStmt()) {
            visit(allow);
        }

        // Close combinations immediately at end of block:
        CoomAst.Constraint c = combBuilder.toConstraint(combBuilder.explanation);
        c.condition = pendingCondition;
        c.scopeType = currentBehaviorScope;
        model.constraints.add(c);
        combBuilder = null;
        pendingCondition = null;
        return null;
    }

    @Override
    public Void visitAllowStmt(CoomParser.AllowStmtContext ctx) {
        if (combBuilder == null) return null;

        List<List<String>> expanded = expandAllowRow(ctx.allowRow());
        combBuilder.allowed.addAll(expanded);
        return null;
    }

    // ---------------- utilities ----------------

    private static String unquote(String s) {
        if (s == null) return null;
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1).replace("\\\"", "\"");
        }
        return s;
    }

    private static String normalizeValue(String raw) {
        return raw != null && raw.startsWith("\"") ? unquote(raw) : raw;
    }

    private static String parseNumFormat(String numToken) {
        if (numToken == null) return null;
        int dot = numToken.indexOf('.');
        return dot >= 0 && dot + 1 < numToken.length() ? numToken.substring(dot + 1) : null;
    }

    private static List<List<String>> expandAllowRow(CoomParser.AllowRowContext row) {
        List<List<String>> tuples = new ArrayList<>();
        if (row == null) {
            tuples.add(new ArrayList<>());
            return tuples;
        }

        tuples.add(new ArrayList<>());
        for (var item : row.allowItem()) {
            List<String> items = new ArrayList<>();
            if (item.value() != null) {
                items.add(normalizeValue(item.value().getText()));
            } else if (item.tupleValue() != null && item.tupleValue().valueList() != null) {
                for (var v : item.tupleValue().valueList().value()) {
                    items.add(normalizeValue(v.getText()));
                }
            } else {
                items.add("");
            }

            List<List<String>> next = new ArrayList<>();
            for (List<String> base : tuples) {
                for (String v : items) {
                    List<String> merged = new ArrayList<>(base);
                    merged.add(v);
                    next.add(merged);
                }
            }
            tuples = next;
        }
        return tuples;
    }
}
