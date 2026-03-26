package org.acme;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

@Path("/")
public class HomeController {

    @GET
    public Response redirectToViewCoom() {
        return Response.seeOther(UriBuilder.fromPath("/view-coom").build()).build();
    }
}
