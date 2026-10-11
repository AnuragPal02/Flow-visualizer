package com.flowviz.adapter;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.flowviz.model.NodeType;
import com.flowviz.parser.AnnotationUtils;
import com.flowviz.parser.EntryPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class QuarkusAdapter implements FrameworkAdapter {

    private static final List<String> VERBS = List.of("GET", "POST", "PUT", "DELETE", "PATCH");
    private static final List<String> CDI_SCOPES = List.of("ApplicationScoped", "RequestScoped", "Singleton", "SessionScoped");

    @Override
    public String frameworkName() {
        return "quarkus";
    }

    @Override
    public boolean appliesTo(ClassOrInterfaceDeclaration clazz) {
        return has(clazz, "Path") || has(clazz, "RegisterRestClient") || CDI_SCOPES.stream().anyMatch(s -> has(clazz, s)) || extendsPanache(clazz);
    }

    @Override
    public List<EntryPoint> detectEntryPoints(ClassOrInterfaceDeclaration clazz) {
        List<EntryPoint> entryPoints = new ArrayList<>();
        Optional<AnnotationExpr> classPath = annotation(clazz, "Path");
        // a @RegisterRestClient interface also has @Path/@GET, but it calls another service, it is not an endpoint
        if (classPath.isEmpty() || has(clazz, "RegisterRestClient")) {
            return entryPoints;
        }
        String prefix = AnnotationUtils.extractStringValue(classPath.get(), "value").orElse("");

        for (MethodDeclaration method : clazz.getMethods()) {
            Optional<String> verb = VERBS.stream().filter(v -> has(method, v)).findFirst();
            if (verb.isEmpty()) continue;

            String path = annotation(method, "Path")
                    .flatMap(a -> AnnotationUtils.extractStringValue(a, "value"))
                    .orElse("");
            entryPoints.add(new EntryPoint(verb.get(), AnnotationUtils.joinPaths(prefix, path), method));
        }
        return entryPoints;
    }

    @Override
    public NodeType classify(ClassOrInterfaceDeclaration clazz) {
        if (has(clazz, "RegisterRestClient")) return NodeType.CLIENT;
        boolean resource = has(clazz, "Path")
                && clazz.getMethods().stream().anyMatch(m -> VERBS.stream().anyMatch(v -> has(m, v)));
        if (resource) return NodeType.CONTROLLER;
        if (extendsPanache(clazz)) return NodeType.REPOSITORY;
        if (CDI_SCOPES.stream().anyMatch(s -> has(clazz, s))) return NodeType.SERVICE;
        return NodeType.OTHER;
    }

    private boolean extendsPanache(ClassOrInterfaceDeclaration clazz) {
        return clazz.getExtendedTypes().stream().anyMatch(t -> t.getNameAsString().startsWith("Panache"))
                || clazz.getImplementedTypes().stream().anyMatch(t -> t.getNameAsString().startsWith("Panache"));
    }

    private boolean has(NodeWithAnnotations<?> node, String name) {
        return annotation(node, name).isPresent();
    }

    private Optional<AnnotationExpr> annotation(NodeWithAnnotations<?> node, String name) {
        return node.getAnnotations().stream().filter(a -> a.getNameAsString().equals(name)).findFirst();
    }
}
