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
import java.util.Map;
import java.util.Optional;

public class SpringAdapter implements FrameworkAdapter {

    private static final Map<String, String> VERB_ANNOTATIONS = Map.of(
            "GetMapping", "GET",
            "PostMapping", "POST",
            "PutMapping", "PUT",
            "DeleteMapping", "DELETE",
            "PatchMapping", "PATCH"
    );

    // Naming heuristic for external-call clients. Good enough for a hackathon;
    // a more thorough version would resolve @FeignClient-annotated interfaces
    // explicitly rather than matching on type name.
    private static final List<String> EXTERNAL_CALL_TYPE_HINTS = List.of(
            "RestTemplate", "WebClient", "FeignClient"
    );

    @Override
    public String frameworkName() {
        return "spring";
    }

    @Override
    public boolean appliesTo(ClassOrInterfaceDeclaration clazz) {
        return hasAnyAnnotation(clazz, "RestController", "Controller", "Service", "Repository", "Component");
    }

    @Override
    public List<EntryPoint> detectEntryPoints(ClassOrInterfaceDeclaration clazz) {
        List<EntryPoint> entryPoints = new ArrayList<>();
        if (!hasAnyAnnotation(clazz, "RestController", "Controller")) {
            return entryPoints;
        }

        String classPrefix = getAnnotation(clazz, "RequestMapping")
                .flatMap(a -> AnnotationUtils.extractStringValue(a, "value", "path"))
                .orElse("");
        String className = clazz.getNameAsString();

        for (MethodDeclaration method : clazz.getMethods()) {
            boolean matchedVerbAnnotation = false;

            for (Map.Entry<String, String> verb : VERB_ANNOTATIONS.entrySet()) {
                Optional<AnnotationExpr> ann = getAnnotation(method, verb.getKey());
                if (ann.isPresent()) {
                    matchedVerbAnnotation = true;
                    String methodPath = AnnotationUtils.extractStringValue(ann.get(), "value", "path").orElse("");
                    String fullPath = AnnotationUtils.joinPaths(classPrefix, methodPath);
                    entryPoints.add(new EntryPoint(verb.getValue(), fullPath, className, method.getNameAsString()));
                }
            }

            // Plain @RequestMapping on a method with no verb specified defaults to GET in Spring.
            if (!matchedVerbAnnotation) {
                getAnnotation(method, "RequestMapping").ifPresent(ann -> {
                    String methodPath = AnnotationUtils.extractStringValue(ann, "value", "path").orElse("");
                    String fullPath = AnnotationUtils.joinPaths(classPrefix, methodPath);
                    entryPoints.add(new EntryPoint("GET", fullPath, className, method.getNameAsString()));
                });
            }
        }

        return entryPoints;
    }

    @Override
    public NodeType classify(ClassOrInterfaceDeclaration clazz) {
        if (hasAnyAnnotation(clazz, "RestController", "Controller")) return NodeType.CONTROLLER;
        if (hasAnyAnnotation(clazz, "Service")) return NodeType.SERVICE;
        if (hasAnyAnnotation(clazz, "Repository")) return NodeType.REPOSITORY;
        if (extendsAny(clazz, "JpaRepository", "CrudRepository", "PagingAndSortingRepository")) return NodeType.REPOSITORY;
        if (hasAnyAnnotation(clazz, "Component")) return NodeType.COMPONENT;
        return NodeType.UNKNOWN;
    }

    @Override
    public boolean isExternalCallType(String qualifiedTypeName) {
        if (qualifiedTypeName == null) return false;
        return EXTERNAL_CALL_TYPE_HINTS.stream().anyMatch(qualifiedTypeName::contains);
    }

    private boolean hasAnyAnnotation(ClassOrInterfaceDeclaration clazz, String... names) {
        for (String name : names) {
            if (getAnnotation(clazz, name).isPresent()) return true;
        }
        return false;
    }

    private boolean extendsAny(ClassOrInterfaceDeclaration clazz, String... typeNames) {
        return matchesAny(clazz.getExtendedTypes().stream().map(t -> t.getNameAsString()), typeNames)
                || matchesAny(clazz.getImplementedTypes().stream().map(t -> t.getNameAsString()), typeNames);
    }

    private boolean matchesAny(java.util.stream.Stream<String> names, String... typeNames) {
        return names.anyMatch(n -> {
            for (String t : typeNames) {
                if (n.equals(t)) return true;
            }
            return false;
        });
    }

    private Optional<AnnotationExpr> getAnnotation(NodeWithAnnotations<?> node, String name) {
        return node.getAnnotations().stream()
                .filter(a -> a.getNameAsString().equals(name))
                .findFirst();
    }
}
