package org.example.coom.compiler.model;

import java.util.*;
import org.example.coom.compiler.Util;

/** AST for the COOM language (simple, pragmatic for your examples). */
public final class CoomAst {

    public enum Kind { PRODUCT, ENUMERATION, STRUCTURE, BEHAVIOR, REQUIRE, IMPLY, DEFAULT, COMBINATIONS, CONDITION_MARK }

    public enum PrimitiveType { BOOL, STRING }

    public static final class CoomModel {
        public final String productName;
        public final ProductDef product = new ProductDef();
        public final Map<String, EnumerationDef> enumerations = new LinkedHashMap<>();
        public final Map<String, StructureDef> structures = new LinkedHashMap<>();
        public final List<Constraint> constraints = new ArrayList<>();

        public CoomModel(String name) {
            this.productName = Util.safeId(Util.capitalize(name));
        }
    }

    public static final class ProductDef {
        public final List<NumAttr> numAttrs = new ArrayList<>();
        public final List<EnumAttr> enumAttrs = new ArrayList<>();
        public final List<PrimitiveAttr> primitiveAttrs = new ArrayList<>();
        public final List<StructRef> structRefs = new ArrayList<>();
    }

    public static final class StructureDef {
        public final String name;
        public final List<NumAttr> numAttrs = new ArrayList<>();
        public final List<EnumAttr> enumAttrs = new ArrayList<>();
        public final List<PrimitiveAttr> primitiveAttrs = new ArrayList<>();
        public final List<StructRef> structRefs = new ArrayList<>();
        public StructureDef(String name) { this.name = Util.safeId(name); }
    }

    public static final class EnumerationDef {
        public final String name;
        public final List<String> enumDataAttrs = new ArrayList<>(); // e.g., ["thickness"]
        public final List<String> valuesNoAttrs = new ArrayList<>();
        public final List<EnumValueWithAttrs> valuesWithAttrs = new ArrayList<>();
        public EnumerationDef(String name) { this.name = Util.safeId(name); }
    }

    public static final class EnumValueWithAttrs {
        public final String name;
        public final Map<String,String> attrValues;
        public EnumValueWithAttrs(String name, Map<String,String> attrValues) {
            this.name = Util.safeId(name);
            this.attrValues = attrValues;
        }
    }

    public static final class NumAttr {
        public final String name; public final int min; public final int max;
        public final String unit; public final String format;
        public NumAttr(String name, int min, int max, String unit, String format) {
            this.name = Util.safeId(name); this.min = min; this.max = max;
            this.unit = unit; this.format = format;
        }
    }

    public static final class EnumAttr {
        public final String name; public final String enumName;
        public EnumAttr(String name, String enumName) {
            this.name = Util.safeId(name); this.enumName = Util.safeId(enumName);
        }
    }

    public static final class PrimitiveAttr {
        public final String name; public final PrimitiveType type;
        public PrimitiveAttr(String name, PrimitiveType type) {
            this.name = Util.safeId(name); this.type = type;
        }
    }

    public static final class StructRef {
        public final String name; public final String typeName; public final int minCard; public final int maxCard;
        public StructRef(String name, String typeName, int minCard, int maxCard) {
            this.name = Util.safeId(name); this.typeName = Util.safeId(typeName);
            this.minCard = minCard; this.maxCard = maxCard;
        }
    }

    public static final class Constraint {
        public final Kind kind;
        public final String expression;
        public String explanation; // optional
        public String condition;   // optional (folded into next rule)
        public String scopeType;   // optional (behavior <Type> {...})
        public final List<String> variables = new ArrayList<>();
        public final List<List<String>> allowedTuples = new ArrayList<>();

        private Constraint(Kind kind, String expr) { this.kind = kind; this.expression = expr; }

        public static Constraint require(String expr, String expl) {
            Constraint c = new Constraint(Kind.REQUIRE, expr); c.explanation = expl; return c;
        }
        public static Constraint imply(String expr, String expl) {
            Constraint c = new Constraint(Kind.IMPLY, expr); c.explanation = expl; return c;
        }
        public static Constraint defaultAssign(String expr, String expl) {
            Constraint c = new Constraint(Kind.DEFAULT, expr); c.explanation = expl; return c;
        }
        public static Constraint combinations(List<String> vars, List<List<String>> tuples, String expl) {
            Constraint c = new Constraint(Kind.COMBINATIONS, "combinations");
            c.variables.addAll(vars); c.allowedTuples.addAll(tuples); c.explanation = expl; return c;
        }
        public static Constraint conditionMarker(String cond) {
            Constraint c = new Constraint(Kind.CONDITION_MARK, null); c.condition = cond; return c;
        }
    }

    public static final class CombinationsBuilder {
        public final List<String> variables = new ArrayList<>();
        public final List<List<String>> allowed = new ArrayList<>();
        public String explanation;
        public Constraint toConstraint(String trailingExplanation) {
            String expl = (explanation != null) ? explanation : trailingExplanation;
            return Constraint.combinations(variables, allowed, expl);
        }
    }

    /** Parser block context holder. */
    public static final class Block {
        public final Kind kind; public final String name;
        Block(Kind k, String n) { this.kind = k; this.name = n; }
        public static Block product() { return new Block(Kind.PRODUCT, null); }
        public static Block enumeration(String n) { return new Block(Kind.ENUMERATION, n); }
        public static Block structure(String n) { return new Block(Kind.STRUCTURE, n); }
        public static Block behavior() { return new Block(Kind.BEHAVIOR, null); }
    }
}
