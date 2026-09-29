package com.flowviz.adapter;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

import java.util.List;
import java.util.Optional;

public class AdapterRegistry {

    private final List<FrameworkAdapter> adapters = List.of(new SpringAdapter(), new QuarkusAdapter());

    /**
     * Returns the adapter that recognizes this class, if any. If more than one
     * matches (e.g. Quarkus's quarkus-spring-web compatibility extension mixing
     * Spring annotations on top of a Quarkus app), the caller is responsible for
     * logging that ambiguity — this just returns the first match deterministically.
     */
    public Optional<FrameworkAdapter> resolve(ClassOrInterfaceDeclaration clazz) {
        return adapters.stream().filter(a -> a.appliesTo(clazz)).findFirst();
    }

    public List<FrameworkAdapter> all() {
        return adapters;
    }
}
