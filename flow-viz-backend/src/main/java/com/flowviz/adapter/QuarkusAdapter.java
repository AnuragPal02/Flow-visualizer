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

    private static final List<String> HTTP_VERB_ANNOTATIONS = List.of("GET", "POST", "PUT", "DELETE", "PATCH");
    private static final List<String> CDI_SCOPES = List.of("ApplicationScoped", "RequestScoped", "Singleton", "SessionScoped");

    @Override
    public String frameworkName() {
        return "quarkus";
    }

    @Override
    public boolean appliesTo(ClassOrInterfaceDeclaration clazz) {
        return hasAnnotation(clazz, "Path")
                || CDI_SCOPES.stream().anyMatch(scope -> hasAnnotation(clazz, scope))
                || extendsPanache(clazz);
    }

    @Override
    public List<EntryPoint> detectEntryPoints(ClassOrInterfaceDeclaration clazz) {
        List<EntryPoint> entryPoints = new ArrayList<>();
        Optional<AnnotationExpr> classPath = getAnnotation(clazz, "Path");
        if (classPath.isEmpty()) {
            return entryPoints;
        }

        String classPrefix = AnnotationUtils.extractStringValue(classPath.get(), "value").orElse("");
        String className = clazz.getNameAsString();

        for (MethodDeclaration method : clazz.getMethods()) {
            Optional<String> httpVerb = HTTP_VERB_ANNOTATIONS.stream()
                    .filter(v -> hasAnnotation(method, v))
                    .findFirst();
            if (httpVerb.isEmpty()) {
                continue;
            }

            String methodPath = getAnnotation(method, "Path")
                    .flatMap(a -> AnnotationUtils.extractStringValue(a, "value"))
                    .orElse("");
            String fullPath = AnnotationUtils.joinPaths(classPrefix, methodPath);
            entryPoints.add(new EntryPoint(httpVerb.get(), fullPath, className, method.getNameAsString()));
        }

        return entryPoints;
    }

    @Override
    public NodeType classify(ClassOrInterfaceDeclaration clazz) {
        boolean hasResourcePath = hasAnnotation(clazz, "Path");
        boolean hasVerbMethod = clazz.getMethods().stream()
                .anyMatch(m -> HTTP_VERB_ANNOTATIONS.stream().anyMatch(v -> hasAnnotation(m, v)));
        if (hasResourcePath && hasVerbMethod) {
            return NodeType.CONTROLLER;
        }

        if (extendsPanache(clazz)) {
            return NodeType.REPOSITORY;
        }

        if (CDI_SCOPES.stream().anyMatch(scope -> hasAnnotation(clazz, scope))) {
            return NodeType.SERVICE;
        }

        return NodeType.UNKNOWN;
    }

    @Override
    public boolean isExternalCallType(String qualifiedTypeName) {
        // MicroProfile Rest Client interfaces are injected via @RestClient on the field,
        // which isn't visible from the call-target's own qualified name alone. Naming
        // heuristic fallback — tighten this if your target repo doesn't follow the
        // "XyzClient" convention for REST client interfaces.
        return qualifiedTypeName != null && qualifiedTypeName.endsWith("Client");
    }

    private boolean extendsPanache(ClassOrInterfaceDeclaration clazz) {
        boolean extendsPanacheType = clazz.getExtendedTypes().stream()
                .anyMatch(t -> t.getNameAsString().startsWith("Panache"));
        boolean implementsPanacheRepo = clazz.getImplementedTypes().stream()
                .anyMatch(t -> t.getNameAsString().startsWith("PanacheRepository"));
        return extendsPanacheType || implementsPanacheRepo;
    }

    private boolean hasAnnotation(NodeWithAnnotations<?> node, String name) {
        return getAnnotation(node, name).isPresent();
    }

    private Optional<AnnotationExpr> getAnnotation(NodeWithAnnotations<?> node, String name) {
        return node.getAnnotations().stream()
                .filter(a -> a.getNameAsString().equals(name))
                .findFirst();
    }
}
