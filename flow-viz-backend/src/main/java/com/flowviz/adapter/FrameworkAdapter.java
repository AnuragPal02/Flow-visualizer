package com.flowviz.adapter;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.flowviz.model.NodeType;
import com.flowviz.parser.EntryPoint;

import java.util.List;

/**
 * A framework-specific strategy for recognizing entry points and classifying
 * classes. The call-graph walker never needs to know which framework it's
 * looking at — it only ever asks "does some adapter recognize this class,
 * and if so what NodeType is it?"
 *
 * Only the two adapters below (Spring, Quarkus) know about actual annotation
 * names. Adding a third framework later means writing one more adapter, not
 * touching the walker or the JSON schema.
 */
public interface FrameworkAdapter {

    String frameworkName();

    /**
     * Quick check: does this class carry annotations/supertypes this adapter
     * recognizes at all? Called against every class in the repo, so keep it cheap.
     */
    boolean appliesTo(ClassOrInterfaceDeclaration clazz);

    /**
     * Find HTTP entry points declared on this class. Only meaningful if
     * appliesTo() was true; returns an empty list for non-resource classes
     * (services, repositories, etc.) recognized by this same adapter.
     */
    List<EntryPoint> detectEntryPoints(ClassOrInterfaceDeclaration clazz);

    /**
     * Classify a recognized class into a NodeType using this framework's
     * conventions (e.g. Spring's @Service vs Quarkus's @ApplicationScoped).
     */
    NodeType classify(ClassOrInterfaceDeclaration clazz);

    /**
     * True if a resolved call target's qualified type name looks like this
     * framework's "call another service" client type (RestTemplate/WebClient/
     * FeignClient for Spring, @RestClient interfaces for Quarkus). Used when
     * the declaring class isn't in the target repo at all.
     */
    boolean isExternalCallType(String qualifiedTypeName);
}
