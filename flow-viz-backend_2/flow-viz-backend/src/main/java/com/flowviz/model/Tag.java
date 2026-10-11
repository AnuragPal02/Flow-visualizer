package com.flowviz.model;

/** A behaviour the framework adds to a method: transaction, cache, async, security, resilience. */
public class Tag {
    public final String kind;
    public final String text;

    public Tag(String kind, String text) {
        this.kind = kind;
        this.text = text;
    }
}
