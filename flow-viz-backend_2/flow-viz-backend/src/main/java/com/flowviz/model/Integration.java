package com.flowviz.model;

/** A place where a step reaches outside the application: HTTP, messaging, database template, email... */
public class Integration {
    public final String kind;   // http | messaging | event | database | email
    public final String client; // RestTemplate, KafkaTemplate, "Feign client", ...
    public final String detail; // method called on the client, or the client's name/url
    public final int line;
    public final String code;

    public Integration(String kind, String client, String detail, int line, String code) {
        this.kind = kind;
        this.client = client;
        this.detail = detail;
        this.line = line;
        this.code = code;
    }
}
