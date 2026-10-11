package com.example.quarkus;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

@Path("/items")
public class ItemResource {

    @Inject
    ItemService itemService;

    @GET
    @Path("/{id}")
    public Object get(@PathParam("id") Long id) {
        return itemService.find(id);
    }
}
