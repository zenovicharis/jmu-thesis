package org.example.coom.compiler.rdf;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.example.coom.compiler.Util;
import org.example.coom.compiler.model.CoomAst;

import java.util.Map;

/** Builds a Jena Model from the COOM AST. */
public final class RdfBuilder {

    public Model build(CoomAst.CoomModel model, String productName) {
        Model m = ModelFactory.createDefaultModel();
        final String COOM = "http://example.com/schema/coom#";
        final String EX = "http://example.com/coom/" + productName + "#";

        // Prefixes
        m.setNsPrefix("coom", COOM);
        m.setNsPrefix("ex", EX);
        m.setNsPrefix("rdfs", RDFS.getURI());

        // Classes
        Resource coomProductCls   = m.createResource(COOM + "Product");
        Resource coomEnumCls      = m.createResource(COOM + "Enumeration");
        Resource coomEnumValCls   = m.createResource(COOM + "EnumerationValue");
        Resource coomStructCls    = m.createResource(COOM + "Structure");
        Resource coomNumAttrCls   = m.createResource(COOM + "NumericAttribute");
        Resource coomEnumAttrCls  = m.createResource(COOM + "EnumAttribute");
        Resource coomPrimAttrCls  = m.createResource(COOM + "PrimitiveAttribute");
        Resource coomConstraintCls= m.createResource(COOM + "Constraint");
        Resource coomRequireCls   = m.createResource(COOM + "Require");
        Resource coomImplyCls     = m.createResource(COOM + "Imply");
        Resource coomDefaultCls   = m.createResource(COOM + "Default");
        Resource coomCombCls      = m.createResource(COOM + "Combinations");

        // Properties
        Property hasAttribute = m.createProperty(COOM, "hasAttribute");
        Property hasStructure = m.createProperty(COOM, "hasStructure");
        Property ofEnumeration= m.createProperty(COOM, "ofEnumeration");
        Property ofType       = m.createProperty(COOM, "ofType");
        Property minP         = m.createProperty(COOM, "min");
        Property maxP         = m.createProperty(COOM, "max");
        Property unitP        = m.createProperty(COOM, "unit");
        Property formatP      = m.createProperty(COOM, "format");
        Property minCardP     = m.createProperty(COOM, "minCardinality");
        Property maxCardP     = m.createProperty(COOM, "maxCardinality");
        Property hasConstraint= m.createProperty(COOM, "hasConstraint");
        Property expressionP  = m.createProperty(COOM, "expression");
        Property explanationP = m.createProperty(COOM, "explanation");
        Property conditionP   = m.createProperty(COOM, "condition");
        Property variablesP   = m.createProperty(COOM, "variables");
        Property allowsP      = m.createProperty(COOM, "allows");

        // Product
        Resource productRes = m.createResource(EX + model.productName)
                .addProperty(RDF.type, coomProductCls);

        // Numeric attributes
        for (CoomAst.NumAttr a : model.product.numAttrs) {
            Resource ar = m.createResource(EX + a.name)
                    .addProperty(RDF.type, coomNumAttrCls)
                    .addProperty(minP, m.createTypedLiteral(a.min, XSDDatatype.XSDinteger))
                    .addProperty(maxP, m.createTypedLiteral(a.max, XSDDatatype.XSDinteger));
            if (a.unit != null) ar.addProperty(unitP, a.unit);
            if (a.format != null) ar.addProperty(formatP, a.format);
            productRes.addProperty(hasAttribute, ar);
        }

        for (CoomAst.PrimitiveAttr a : model.product.primitiveAttrs) {
            Resource ar = m.createResource(EX + a.name)
                    .addProperty(RDF.type, coomPrimAttrCls)
                    .addProperty(RDFS.range, a.type == CoomAst.PrimitiveType.BOOL
                            ? m.createResource(XSDDatatype.XSDboolean.getURI())
                            : m.createResource(XSDDatatype.XSDstring.getURI()));
            productRes.addProperty(hasAttribute, ar);
        }

        // Enum attributes
        for (CoomAst.EnumAttr a : model.product.enumAttrs) {
            Resource ar = m.createResource(EX + a.name).addProperty(RDF.type, coomEnumAttrCls);
            Resource enumRes = m.createResource(EX + a.enumName).addProperty(RDF.type, coomEnumCls);
            ar.addProperty(ofEnumeration, enumRes);
            productRes.addProperty(hasAttribute, ar);
        }

        // Structure refs
        for (CoomAst.StructRef s : model.product.structRefs) {
            Resource ref = m.createResource(EX + s.name)
                    .addProperty(RDF.type, coomStructCls)
                    .addProperty(ofType, m.createResource(EX + s.typeName))
                    .addProperty(minCardP, m.createTypedLiteral(s.minCard, XSDDatatype.XSDinteger))
                    .addProperty(maxCardP, m.createTypedLiteral(s.maxCard, XSDDatatype.XSDinteger));
            productRes.addProperty(hasStructure, ref);
        }

        // Enumerations + values
        for (CoomAst.EnumerationDef ed : model.enumerations.values()) {
            Resource enumRes = m.createResource(EX + ed.name).addProperty(RDF.type, coomEnumCls);

            for (String val : ed.valuesNoAttrs) {
                m.createResource(EX + ed.name + "-" + val)
                        .addProperty(RDF.type, coomEnumValCls)
                        .addProperty(RDFS.label, val)
                        .addProperty(RDFS.member, enumRes);
            }
            for (CoomAst.EnumValueWithAttrs v : ed.valuesWithAttrs) {
                Resource vr = m.createResource(EX + ed.name + "-" + v.name)
                        .addProperty(RDF.type, coomEnumValCls)
                        .addProperty(RDFS.label, v.name)
                        .addProperty(RDFS.member, enumRes);
                for (Map.Entry<String, String> e : v.attrValues.entrySet()) {
                    Property p = m.createProperty(EX, e.getKey());
                    if (Util.isInteger(e.getValue())) {
                        vr.addLiteral(p, m.createTypedLiteral(Integer.parseInt(e.getValue()), XSDDatatype.XSDinteger));
                    } else {
                        vr.addLiteral(p, e.getValue());
                    }
                }
            }
        }

        // Structure definitions
        for (CoomAst.StructureDef sd : model.structures.values()) {
            Resource sr = m.createResource(EX + sd.name).addProperty(RDF.type, coomStructCls);
            for (CoomAst.NumAttr a : sd.numAttrs) {
                Resource ar = m.createResource(EX + sd.name + "-" + a.name)
                        .addProperty(RDF.type, coomNumAttrCls)
                        .addProperty(minP, m.createTypedLiteral(a.min, XSDDatatype.XSDinteger))
                        .addProperty(maxP, m.createTypedLiteral(a.max, XSDDatatype.XSDinteger));
                if (a.unit != null) ar.addProperty(unitP, a.unit);
                if (a.format != null) ar.addProperty(formatP, a.format);
                sr.addProperty(hasAttribute, ar);
            }
            for (CoomAst.PrimitiveAttr a : sd.primitiveAttrs) {
                Resource ar = m.createResource(EX + sd.name + "-" + a.name)
                        .addProperty(RDF.type, coomPrimAttrCls)
                        .addProperty(RDFS.range, a.type == CoomAst.PrimitiveType.BOOL
                                ? m.createResource(XSDDatatype.XSDboolean.getURI())
                                : m.createResource(XSDDatatype.XSDstring.getURI()));
                sr.addProperty(hasAttribute, ar);
            }
            for (CoomAst.EnumAttr a : sd.enumAttrs) {
                Resource ar = m.createResource(EX + sd.name + "-" + a.name)
                        .addProperty(RDF.type, coomEnumAttrCls)
                        .addProperty(ofEnumeration, m.createResource(EX + a.enumName));
                sr.addProperty(hasAttribute, ar);
            }
            for (CoomAst.StructRef s : sd.structRefs) {
                Resource ref = m.createResource(EX + sd.name + "-" + s.name)
                        .addProperty(RDF.type, coomStructCls)
                        .addProperty(ofType, m.createResource(EX + s.typeName))
                        .addProperty(minCardP, m.createTypedLiteral(s.minCard, XSDDatatype.XSDinteger))
                        .addProperty(maxCardP, m.createTypedLiteral(s.maxCard, XSDDatatype.XSDinteger));
                sr.addProperty(hasStructure, ref);
            }
        }

        // Behavior -> constraints
        for (CoomAst.Constraint c : model.constraints) {
            Resource cr = m.createResource()
                    .addProperty(RDF.type, coomConstraintCls);
            if (c.expression != null) cr.addProperty(expressionP, c.expression);
            if (c.explanation != null) cr.addProperty(explanationP, c.explanation);
            if (c.condition != null) cr.addProperty(conditionP, c.condition);

            switch (c.kind) {
                case REQUIRE -> cr.addProperty(RDF.type, coomRequireCls);
                case IMPLY   -> cr.addProperty(RDF.type, coomImplyCls);
                case DEFAULT -> cr.addProperty(RDF.type, coomDefaultCls);
                case COMBINATIONS -> {
                    cr.addProperty(RDF.type, coomCombCls);
                    if (!c.variables.isEmpty()) {
                        cr.addProperty(variablesP, "(" + String.join(",", c.variables) + ")");
                    }
                    for (var tuple : c.allowedTuples) {
                        cr.addProperty(allowsP, "(" + String.join(",", tuple) + ")");
                    }
                }
                default -> {}
            }
            productRes.addProperty(hasConstraint, cr);
        }

        return m;
    }
}
