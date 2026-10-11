package com.flowviz.model;

/** Kind of application class a node belongs to. Only the app's own code is ever graphed. */
public enum NodeType {
    CONTROLLER,
    SERVICE,
    REPOSITORY,
    CLIENT,     // app-owned interface for calling another service (Feign / MicroProfile REST client)
    COMPONENT,
    OTHER
}
