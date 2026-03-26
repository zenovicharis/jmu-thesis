package org.acme;

import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/sparql")
public class SparqlController {

    @Inject
    Template sparql;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String page() {
        return sparql
                .data("title", "SPARQL Playground")
                .data("items", java.util.Collections.emptyList())
                .data("bindings", java.util.Collections.emptyList())
                .render();
    }
}
