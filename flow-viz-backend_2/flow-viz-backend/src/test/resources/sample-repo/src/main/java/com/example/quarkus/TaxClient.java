package com.example.quarkus;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "tax")
@Path("/tax")
public interface TaxClient {
    @GET
    @Path("/{region}")
    Double rate(@PathParam("region") String region);
}
