package com.flowviz.adapter;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.flowviz.model.NodeType;

import java.util.List;
import java.util.Optional;

public class AdapterRegistry {

    private final List<FrameworkAdapter> adapters = List.of(new SpringAdapter(), new QuarkusAdapter());

    public List<FrameworkAdapter> all() {
        return adapters;
    }

    /** The framework a class belongs to, if it carries any framework annotation. */
    public Optional<FrameworkAdapter> resolve(ClassOrInterfaceDeclaration clazz) {
        return adapters.stream().filter(a -> a.appliesTo(clazz)).findFirst();
    }

    /** Annotation first (@Service, @Repository, ...), then class-name/supertype hints, else OTHER. */
    public NodeType classify(ClassOrInterfaceDeclaration clazz) {
        for (FrameworkAdapter adapter : adapters) {
            if (adapter.appliesTo(clazz)) {
                NodeType type = adapter.classify(clazz);
                if (type != NodeType.OTHER) return type;
            }
        }
        return byNaming(clazz);
    }

    private NodeType byNaming(ClassOrInterfaceDeclaration clazz) {
        String name = clazz.getNameAsString();
        if (name.endsWith("Repository") || name.endsWith("Repo") || name.endsWith("Dao") || name.endsWith("DAO")) {
            return NodeType.REPOSITORY;
        }
        // e.g. "interface OrderRepository extends JpaRepository<..>" or "implements PanacheRepository<..>"
        for (ClassOrInterfaceType t : clazz.getExtendedTypes()) {
            if (looksLikeRepositoryType(t.getNameAsString())) return NodeType.REPOSITORY;
        }
        for (ClassOrInterfaceType t : clazz.getImplementedTypes()) {
            if (looksLikeRepositoryType(t.getNameAsString())) return NodeType.REPOSITORY;
        }
        if (name.endsWith("Service") || name.endsWith("ServiceImpl")) return NodeType.SERVICE;
        if (name.endsWith("Controller")) return NodeType.CONTROLLER;
        return NodeType.OTHER;
    }

    private boolean looksLikeRepositoryType(String typeName) {
        return typeName.endsWith("Repository") || typeName.startsWith("Panache");
    }
}
