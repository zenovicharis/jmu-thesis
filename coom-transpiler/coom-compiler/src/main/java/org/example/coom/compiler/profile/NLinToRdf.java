package org.example.coom.compiler.profile;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convert N-LIN lines (canonical facts) into RDF.
 *
 * This is intentionally pragmatic for demo purposes:
 * - deterministic URIs
 * - small, readable vocabulary under https://example.org/coom#
 *
 * You can later align the vocabulary to your thesis ontology.
 */
public final class NLinToRdf {

    // Namespace for demo
    public static final String NS = "https://example.org/coom#";

    // Classes
    private static final Resource C_MODEL      = ResourceFactory.createResource(NS + "Model");
    private static final Resource C_PRODUCT    = ResourceFactory.createResource(NS + "ProductDecl");
    private static final Resource C_STRUCTURE  = ResourceFactory.createResource(NS + "Structure");
    private static final Resource C_ENUM       = ResourceFactory.createResource(NS + "Enumeration");
    private static final Resource C_ENUMVALUE  = ResourceFactory.createResource(NS + "EnumValue");
    private static final Resource C_CONSTRAINT = ResourceFactory.createResource(NS + "Constraint");

    // Properties
    private static final Property P_HAS_PRODUCT_ATTR = ResourceFactory.createProperty(NS, "hasProductAttr");
    private static final Property P_HAS_STRUCT_ATTR  = ResourceFactory.createProperty(NS, "hasStructAttr");
    private static final Property P_TYPE             = ResourceFactory.createProperty(NS, "type");
    private static final Property P_HAS_VALUE        = ResourceFactory.createProperty(NS, "hasValue");
    private static final Property P_KIND             = ResourceFactory.createProperty(NS, "kind");
    private static final Property P_EXPR             = ResourceFactory.createProperty(NS, "expr");
    private static final Property P_HAS_CONSTRAINT   = ResourceFactory.createProperty(NS, "hasConstraint");
    private static final Property P_TARGET           = ResourceFactory.createProperty(NS, "target");

    private static final Property P_HAS_STRUCT       = ResourceFactory.createProperty(NS, "hasStructure");
    private static final Property P_HAS_ENUM         = ResourceFactory.createProperty(NS, "hasEnumeration");

    // N-LIN patterns we currently output
    private static final Pattern MODEL_P = Pattern.compile("^MODEL\\s+(\\S+)$");
    private static final Pattern PRODUCT_ENUMATTR_P = Pattern.compile("^PRODUCT\\s+ENUMATTR\\s+(\\S+)\\s+TYPE\\s+(\\S+)$");
    private static final Pattern STRUCT_P = Pattern.compile("^STRUCT\\s+(\\S+)$");
    private static final Pattern STRUCT_ENUMATTR_P = Pattern.compile("^STRUCT\\s+(\\S+)\\s+ENUMATTR\\s+(\\S+)\\s+TYPE\\s+(\\S+)$");
    private static final Pattern ENUM_P = Pattern.compile("^ENUM\\s+(\\S+)$");
    private static final Pattern ENUM_VALUE_P = Pattern.compile("^ENUM\\s+(\\S+)\\s+VALUE\\s+(\\S+).*$");
    private static final Pattern CONSTRAINT_P = Pattern.compile("^CONSTRAINT\\s+(\\S+)\\s+(.*)$");

    public Model toRdf(List<String> nlinLines) {
        Objects.requireNonNull(nlinLines, "nlinLines");

        Model m = ModelFactory.createDefaultModel();
        m.setNsPrefix("coom", NS);

        // Create/lookup resources as we discover them
        Resource modelRes = null;

        Map<String, Resource> structures = new HashMap<>();
        Map<String, Resource> enums = new HashMap<>();
        Map<String, Resource> enumValues = new HashMap<>();

        // Ensure we have a product decl node per model (for grouping)
        Resource productDecl = null;

        int constraintCounter = 0;

        for (String raw : nlinLines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            Matcher mm = MODEL_P.matcher(line);
            if (mm.find()) {
                String modelName = mm.group(1);
                modelRes = iri(m, "model/" + modelName);
                modelRes.addProperty(RDF.type, C_MODEL);
                modelRes.addProperty(ResourceFactory.createProperty(NS, "name"), modelName);

                productDecl = iri(m, "model/" + modelName + "/product");
                productDecl.addProperty(RDF.type, C_PRODUCT);

                modelRes.addProperty(ResourceFactory.createProperty(NS, "product"), productDecl);
                continue;
            }

            Matcher pe = PRODUCT_ENUMATTR_P.matcher(line);
            if (pe.find()) {
                ensureModel(modelRes);

                String attrName = pe.group(1);
                String typeName = pe.group(2);

                Resource attrRes = iri(m, "productAttr/" + attrName);
                attrRes.addProperty(RDF.type, ResourceFactory.createResource(NS + "Attribute"));
                attrRes.addProperty(ResourceFactory.createProperty(NS, "name"), attrName);
                attrRes.addProperty(P_TYPE, typeName);

                productDecl.addProperty(P_HAS_PRODUCT_ATTR, attrRes);

                // Link model to the structure type if it exists later
                // (we will also link structures as we parse STRUCT lines)
                continue;
            }

            Matcher sp = STRUCT_P.matcher(line);
            if (sp.find()) {
                ensureModel(modelRes);

                String structName = sp.group(1);
                Resource sRes = structures.computeIfAbsent(structName, k -> {
                    Resource r = iri(m, "structure/" + k);
                    r.addProperty(RDF.type, C_STRUCTURE);
                    r.addProperty(ResourceFactory.createProperty(NS, "name"), k);
                    return r;
                });

                modelRes.addProperty(P_HAS_STRUCT, sRes);
                continue;
            }

            Matcher se = STRUCT_ENUMATTR_P.matcher(line);
            if (se.find()) {
                ensureModel(modelRes);

                String structName = se.group(1);
                String attrName = se.group(2);
                String enumType = se.group(3);

                Resource sRes = structures.computeIfAbsent(structName, k -> {
                    Resource r = iri(m, "structure/" + k);
                    r.addProperty(RDF.type, C_STRUCTURE);
                    r.addProperty(ResourceFactory.createProperty(NS, "name"), k);
                    return r;
                });

                Resource attrRes = iri(m, "structAttr/" + structName + "/" + attrName);
                attrRes.addProperty(RDF.type, ResourceFactory.createResource(NS + "Attribute"));
                attrRes.addProperty(ResourceFactory.createProperty(NS, "name"), attrName);
                attrRes.addProperty(P_TYPE, enumType);

                sRes.addProperty(P_HAS_STRUCT_ATTR, attrRes);

                // Ensure enumeration resource exists
                Resource eRes = enums.computeIfAbsent(enumType, k -> {
                    Resource r = iri(m, "enum/" + k);
                    r.addProperty(RDF.type, C_ENUM);
                    r.addProperty(ResourceFactory.createProperty(NS, "name"), k);
                    return r;
                });

                modelRes.addProperty(P_HAS_ENUM, eRes);
                continue;
            }

            Matcher ep = ENUM_P.matcher(line);
            if (ep.find()) {
                ensureModel(modelRes);

                String enumName = ep.group(1);
                Resource eRes = enums.computeIfAbsent(enumName, k -> {
                    Resource r = iri(m, "enum/" + k);
                    r.addProperty(RDF.type, C_ENUM);
                    r.addProperty(ResourceFactory.createProperty(NS, "name"), k);
                    return r;
                });

                modelRes.addProperty(P_HAS_ENUM, eRes);
                continue;
            }

            Matcher ev = ENUM_VALUE_P.matcher(line);
            if (ev.find()) {
                ensureModel(modelRes);

                String enumName = ev.group(1);
                String valueName = ev.group(2);

                Resource eRes = enums.computeIfAbsent(enumName, k -> {
                    Resource r = iri(m, "enum/" + k);
                    r.addProperty(RDF.type, C_ENUM);
                    r.addProperty(ResourceFactory.createProperty(NS, "name"), k);
                    return r;
                });

                String key = enumName + "." + valueName;
                Resource vRes = enumValues.computeIfAbsent(key, k -> {
                    Resource r = iri(m, "enumValue/" + enumName + "/" + valueName);
                    r.addProperty(RDF.type, C_ENUMVALUE);
                    r.addProperty(ResourceFactory.createProperty(NS, "name"), valueName);
                    r.addProperty(P_TARGET, enumName);
                    return r;
                });

                eRes.addProperty(P_HAS_VALUE, vRes);
                continue;
            }

            Matcher cp = CONSTRAINT_P.matcher(line);
            if (cp.find()) {
                ensureModel(modelRes);

                String kind = cp.group(1);
                String expr = cp.group(2).trim();

                Resource cRes = iri(m, "constraint/" + (++constraintCounter));
                cRes.addProperty(RDF.type, C_CONSTRAINT);
                cRes.addProperty(P_KIND, kind);
                cRes.addProperty(P_EXPR, expr);

                modelRes.addProperty(P_HAS_CONSTRAINT, cRes);
                continue;
            }

            // Unknown line: ignore for now (safe for demo)
        }

        return m;
    }

    private static void ensureModel(Resource modelRes) {
        if (modelRes == null) {
            throw new IllegalStateException("N-LIN must start with 'MODEL <name>'");
        }
    }

    private static Resource iri(Model m, String path) {
        // Avoid spaces and keep deterministic IRIs
        String safe = path.replaceAll("\\s+", "_");
        return m.createResource(NS + safe);
    }
}
