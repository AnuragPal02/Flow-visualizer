package com.flowviz.model;

/** One input of the endpoint, read from the controller method signature. */
public class EndpointInput {
    public final String kind;     // path | query | body | header | cookie | form
    public final String name;
    public final String type;
    public final boolean required;

    public EndpointInput(String kind, String name, String type, boolean required) {
        this.kind = kind;
        this.name = name;
        this.type = type;
        this.required = required;
    }
}
