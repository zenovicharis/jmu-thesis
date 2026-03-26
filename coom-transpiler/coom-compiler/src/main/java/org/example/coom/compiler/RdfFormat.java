package org.example.coom.compiler;

import org.apache.jena.riot.Lang;

public enum RdfFormat {
    TURTLE(Lang.TURTLE, "text/turtle", "ttl"),
    RDFXML(Lang.RDFXML, "application/rdf+xml", "rdf"),
    NTRIPLES(Lang.NTRIPLES, "application/n-triples", "nt"),
    JSONLD(Lang.JSONLD, "application/ld+json", "jsonld");

    public final Lang lang;
    public final String contentType;
    public final String ext;

    RdfFormat(Lang lang, String contentType, String ext) {
        this.lang = lang;
        this.contentType = contentType;
        this.ext = ext;
    }

    public static RdfFormat from(String s) {
        if (s == null) return TURTLE;
        return switch (s.toLowerCase()) {
            case "rdfxml", "rdf/xml" -> RDFXML;
            case "ntriples", "nt" -> NTRIPLES;
            case "jsonld", "json-ld" -> JSONLD;
            default -> TURTLE;
        };
    }
}
