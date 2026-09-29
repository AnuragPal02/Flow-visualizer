package com.flowviz.model;

/**
 * Classification of a node in the call graph. Framework-agnostic — both the
 * SpringAdapter and QuarkusAdapter map their own framework's stereotypes
 * (@Service vs @ApplicationScoped, @Repository vs Panache, etc.) onto this
 * same set of types so the walker and the frontend never need to know which
 * framework a given node came from.
 */
public enum NodeType {
    CONTROLLER,
    SERVICE,
    REPOSITORY,
    EXTERNAL_CALL,
    COMPONENT,
    LIBRARY,
    UNKNOWN
}
