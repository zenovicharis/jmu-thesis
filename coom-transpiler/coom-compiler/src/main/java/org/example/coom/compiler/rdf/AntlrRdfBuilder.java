package org.example.coom.compiler.rdf;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.example.coom.compiler.parse.antlr.CoomParser;
import org.example.coom.compiler.parse.antlr.CoomParserBaseVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Direct ANTLR parse-tree -> RDF builder.
 * If you prefer ANTLR -> CoomAst -> RdfBuilder, you can skip this and use CoomAstBuilder instead.
 */
public final class AntlrRdfBuilder extends CoomParserBaseVisitor<Void> {

    private final Model m;
    private final String base;
    private final String COOM = "https://example.org/coom#";

    private final Resource modelRes;
    private final Resource productType;

    private final Property hasEnumeration;
    private final Property hasStructure;
    private final Property hasConstraint;

    private final Property minP;
    private final Property maxP;

    private final Property kindP;
    private final Property exprP;
    private final Property explanationP;
    private final Property conditionP;

    private final Property variablesP;
    private final Property allowedP;
    private final Property allowValueP;

    private String currentEnumName = null;
    private String currentStructureName = null;
    private final List<String> declaredEnums = new ArrayList<>();
    private final List<String> declaredStructures = new ArrayList<>();

    private String pendingExplanation = null;
    private String pendingCondition = null;

    private List<String> combVars = null;
    private List<List<String>> combAllowed = null;
    private String combExplanation = null;

    private int constraintIndex = 0;

    public AntlrRdfBuilder(String baseIri, String productName) {
        this.base = normalizeBase(baseIri);

        this.m = ModelFactory.createDefaultModel();
        m.setNsPrefix("coom", COOM);
        m.setNsPrefix("base", this.base);
        m.setNsPrefix("rdf", RDF.uri);
        m.setNsPrefix("rdfs", RDFS.uri);
        m.setNsPrefix("owl", OWL.NS);

        hasEnumeration = m.createProperty(COOM, "hasEnumeration");
        hasStructure   = m.createProperty(COOM, "hasStructure");
        hasConstraint  = m.createProperty(COOM, "hasConstraint");

        minP = m.createProperty(COOM, "min");
        maxP = m.createProperty(COOM, "max");

        kindP        = m.createProperty(COOM, "kind");
        exprP        = m.createProperty(COOM, "expr");
        explanationP = m.createProperty(COOM, "explanation");
        conditionP   = m.createProperty(COOM, "condition");

        variablesP   = m.createProperty(COOM, "variables");
        allowedP     = m.createProperty(COOM, "allowed");
        allowValueP  = m.createProperty(COOM, "allowValue");

        modelRes = iri("model/" + safe(productName));
        modelRes.addProperty(RDF.type, m.createResource(COOM + "CoomModel"));
        modelRes.addProperty(RDFS.label, productName);

        productType = iri("type/" + safe(productName));
        productType.addProperty(RDF.type, OWL.Class);
        productType.addProperty(RDFS.label, productName);
    }

    public Model getModel() {
        return m;
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

    // ---------- product ----------

    @Override
    public Void visitProductDecl(CoomParser.ProductDeclContext ctx) {
        for (var line : ctx.productLine()) visit(line);
        return null;
    }

    // ---------- numeric decls ----------

    @Override
    public Void visitNumDecl(CoomParser.NumDeclContext ctx) {
        String attr = ctx.IDENT().getText();

        Property p = prop("prop/" + safe(attr));
        p.addProperty(RDF.type, OWL.DatatypeProperty);

        Resource domain = (currentStructureName != null)
                ? iri("structure/" + safe(currentStructureName))
                : productType;

        p.addProperty(RDFS.domain, domain);

        // Alternative A: "num 0-20 length"
        if (ctx.numRange() != null) {
            int min = Integer.parseInt(ctx.numRange().INT(0).getText());
            int max = Integer.parseInt(ctx.numRange().INT(1).getText());
            p.addProperty(minP, m.createTypedLiteral(min));
            p.addProperty(maxP, m.createTypedLiteral(max));
            return null;
        }

        // Alternative B: "num /mm reach" OR "num reach"
        if (ctx.unitSpec() != null) {
            String unit = ctx.unitSpec().IDENT().getText();
            p.addProperty(m.createProperty(COOM, "unit"), unit);
        }

        return null;
    }

    @Override
    public Void visitMultiplicityDecl(CoomParser.MultiplicityDeclContext ctx) {
        int min = Integer.parseInt(ctx.INT(0).getText());
        String maxTok = ctx.getChild(2).getText();
        String type = ctx.IDENT(0).getText();
        String name = ctx.IDENT(1).getText();

        Property p = prop("prop/" + safe(name));
        p.addProperty(RDF.type, OWL.ObjectProperty);
        p.addProperty(RDFS.domain, productType);

        Resource range = iri("structure/" + safe(type));
        p.addProperty(RDFS.range, range);

        p.addProperty(minP, m.createTypedLiteral(min));
        if ("*".equals(maxTok)) p.addProperty(maxP, "*");
        else p.addProperty(maxP, m.createTypedLiteral(Integer.parseInt(maxTok)));

        return null;
    }

    // ---------- typed attributes ----------

    @Override
    public Void visitTypedAttrDecl(CoomParser.TypedAttrDeclContext ctx) {
        String left = ctx.IDENT(0).getText();
        String attr = ctx.IDENT(1).getText();

        boolean isEnumType = declaredEnums.contains(left);
        boolean isStructType = declaredStructures.contains(left);

        if (isEnumType || isStructType) {
            Property p = prop("prop/" + safe(attr));
            p.addProperty(RDF.type, OWL.ObjectProperty);

            Resource domain = (currentStructureName != null)
                    ? iri("structure/" + safe(currentStructureName))
                    : productType;

            p.addProperty(RDFS.domain, domain);

            String ns = isStructType ? "structure/" : "enum/";
            Resource range = iri(ns + safe(left));
            p.addProperty(RDFS.range, range);
        }

        return null;
    }

    @Override
    public Void visitPrimitiveAttrDecl(CoomParser.PrimitiveAttrDeclContext ctx) {
        String type = ctx.getChild(0).getText();
        String attr = ctx.IDENT().getText();

        Property p = prop("prop/" + safe(attr));
        p.addProperty(RDF.type, OWL.DatatypeProperty);

        Resource domain = (currentStructureName != null)
                ? iri("structure/" + safe(currentStructureName))
                : productType;
        p.addProperty(RDFS.domain, domain);

        Resource range = "bool".equals(type) ? m.createResource("http://www.w3.org/2001/XMLSchema#boolean")
                : m.createResource("http://www.w3.org/2001/XMLSchema#string");
        p.addProperty(RDFS.range, range);
        return null;
    }

    // ---------- enumeration ----------

    @Override
    public Void visitEnumerationDecl(CoomParser.EnumerationDeclContext ctx) {
        currentEnumName = ctx.IDENT().getText();

        Resource enumRes = iri("enum/" + safe(currentEnumName));
        enumRes.addProperty(RDF.type, m.createResource(COOM + "Enumeration"));
        enumRes.addProperty(RDFS.label, currentEnumName);

        modelRes.addProperty(hasEnumeration, enumRes);

        for (var line : ctx.enumerationLine()) visit(line);

        currentEnumName = null;
        return null;
    }

    @Override
    public Void visitEnumAttributeDecl(CoomParser.EnumAttributeDeclContext ctx) {
        Resource enumRes = iri("enum/" + safe(currentEnumName));
        String attr = ctx.IDENT().getText();

        Resource da = m.createResource();
        da.addProperty(RDF.type, m.createResource(COOM + "EnumDataAttribute"));
        da.addProperty(RDFS.label, attr);

        enumRes.addProperty(m.createProperty(COOM, "hasDataAttribute"), da);

        // Optionally store unit if provided
        if (ctx.unitSpec() != null) {
            da.addProperty(m.createProperty(COOM, "unit"), ctx.unitSpec().IDENT().getText());
        }

        return null;
    }

    @Override
    public Void visitEnumBareValueDecl(CoomParser.EnumBareValueDeclContext ctx) {
        Resource enumRes = iri("enum/" + safe(currentEnumName));
        String valueName = ctx.IDENT().getText();

        Resource v = iri("enum/" + safe(currentEnumName) + "/value/" + safe(valueName));
        v.addProperty(RDF.type, m.createResource(COOM + "EnumValue"));
        v.addProperty(RDFS.label, valueName);

        enumRes.addProperty(m.createProperty(COOM, "hasValue"), v);
        return null;
    }

    @Override
    public Void visitEnumValueWithAttrsDecl(CoomParser.EnumValueWithAttrsDeclContext ctx) {
        Resource enumRes = iri("enum/" + safe(currentEnumName));
        String valueName = ctx.IDENT().getText();

        Resource v = iri("enum/" + safe(currentEnumName) + "/value/" + safe(valueName));
        v.addProperty(RDF.type, m.createResource(COOM + "EnumValue"));
        v.addProperty(RDFS.label, valueName);

        if (ctx.valueList() != null) {
            int i = 0;
            for (var vv : ctx.valueList().value()) {
                v.addProperty(m.createProperty(COOM, "tupleIndex"), m.createTypedLiteral(i++));
                v.addProperty(m.createProperty(COOM, "tupleValue"), normalizeValue(vv.getText()));
            }
        }

        enumRes.addProperty(m.createProperty(COOM, "hasValue"), v);
        return null;
    }

    // ---------- structure ----------

    @Override
    public Void visitStructureDecl(CoomParser.StructureDeclContext ctx) {
        currentStructureName = ctx.IDENT().getText();

        Resource s = iri("structure/" + safe(currentStructureName));
        s.addProperty(RDF.type, m.createResource(COOM + "Structure"));
        s.addProperty(RDFS.label, currentStructureName);

        modelRes.addProperty(hasStructure, s);

        for (var line : ctx.structureLine()) visit(line);

        currentStructureName = null;
        return null;
    }

    // ---------- behavior ----------

    @Override
    public Void visitBehaviorDecl(CoomParser.BehaviorDeclContext ctx) {
        pendingExplanation = null;
        pendingCondition = null;

        for (var stmt : ctx.behaviorStmt()) visit(stmt);

        return null;
    }

    @Override
    public Void visitExplanationStmt(CoomParser.ExplanationStmtContext ctx) {
        pendingExplanation = unquote(ctx.STRING().getText());
        return null;
    }

    @Override
    public Void visitConditionStmt(CoomParser.ConditionStmtContext ctx) {
        pendingCondition = ctx.expr().getText().trim();
        return null;
    }

    @Override
    public Void visitDefaultStmt(CoomParser.DefaultStmtContext ctx) {
        String expr = ctx.expr().getText().trim();
        emitConstraint("DEFAULT", expr);
        pendingExplanation = null;
        pendingCondition = null;
        return null;
    }

    @Override
    public Void visitRequireStmt(CoomParser.RequireStmtContext ctx) {
        emitConstraint("REQUIRE", ctx.expr().getText().trim());
        pendingExplanation = null;
        pendingCondition = null;
        return null;
    }

    @Override
    public Void visitImplyStmt(CoomParser.ImplyStmtContext ctx) {
        emitConstraint("IMPLY", ctx.expr().getText().trim());
        pendingExplanation = null;
        pendingCondition = null;
        return null;
    }

    @Override
    public Void visitCombinationsBlock(CoomParser.CombinationsBlockContext ctx) {
        combVars = new ArrayList<>();
        combAllowed = new ArrayList<>();
        combExplanation = pendingExplanation;
        pendingExplanation = null;

        if (ctx.identList() != null) {
            for (var path : ctx.identList().path()) combVars.add(path.getText());
        }

        for (var a : ctx.allowStmt()) visit(a);

        emitCombinationsConstraint();
        combVars = null;
        combAllowed = null;
        combExplanation = null;
        pendingCondition = null;
        return null;
    }

    @Override
    public Void visitAllowStmt(CoomParser.AllowStmtContext ctx) {
        if (combAllowed == null) return null;

        combAllowed.addAll(expandAllowRow(ctx.allowRow()));
        return null;
    }

    private void emitConstraint(String kind, String exprText) {
        Resource c = iri("constraint/" + (++constraintIndex));
        c.addProperty(RDF.type, m.createResource(COOM + "Constraint"));
        c.addProperty(kindP, kind);
        c.addProperty(exprP, exprText);

        if (pendingExplanation != null) c.addProperty(explanationP, pendingExplanation);
        if (pendingCondition != null) c.addProperty(conditionP, pendingCondition);

        modelRes.addProperty(hasConstraint, c);
    }

    private void emitCombinationsConstraint() {
        Resource c = iri("constraint/" + (++constraintIndex));
        c.addProperty(RDF.type, m.createResource(COOM + "CombinationsConstraint"));
        c.addProperty(kindP, "COMBINATIONS");

        if (combExplanation != null) c.addProperty(explanationP, combExplanation);
        if (pendingCondition != null) c.addProperty(conditionP, pendingCondition);

        for (String v : combVars) c.addProperty(variablesP, v);

        for (List<String> tuple : combAllowed) {
            Resource t = m.createResource();
            t.addProperty(RDF.type, m.createResource(COOM + "AllowedTuple"));
            for (String tv : tuple) t.addProperty(allowValueP, tv);
            c.addProperty(allowedP, t);
        }

        modelRes.addProperty(hasConstraint, c);
    }

    // ---------- helpers ----------

    private Resource iri(String local) {
        return m.createResource(base + local);
    }

    private Property prop(String local) {
        return m.createProperty(base + local);
    }

    private static String normalizeBase(String baseIri) {
        if (baseIri == null || baseIri.isBlank()) return "https://example.org/base#";
        return (baseIri.endsWith("#") || baseIri.endsWith("/")) ? baseIri : baseIri + "#";
    }

    private static String safe(String s) {
        return s == null ? "X" : s.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    private static String unquote(String s) {
        if (s == null) return null;
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1).replace("\\\"", "\"");
        }
        return s;
    }

    private static String normalizeValue(String raw) {
        if (raw == null) return "";
        raw = raw.trim();
        return raw.startsWith("\"") ? unquote(raw) : raw;
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
