package com.flowviz.adapter;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.flowviz.model.NodeType;
import com.flowviz.parser.AnnotationUtils;
import com.flowviz.parser.EntryPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpringAdapter implements FrameworkAdapter {

    private static final Map<String, String> VERB_ANNOTATIONS = Map.of(
            "GetMapping", "GET",
            "PostMapping", "POST",
            "PutMapping", "PUT",
            "DeleteMapping", "DELETE",
            "PatchMapping", "PATCH"
    );
    private static final Pattern VERB = Pattern.compile("GET|POST|PUT|DELETE|PATCH");

    @Override
    public String frameworkName() {
        return "spring";
    }

    @Override
    public boolean appliesTo(ClassOrInterfaceDeclaration clazz) {
        return hasAny(clazz, "RestController", "Controller", "Service", "Repository", "Component", "FeignClient");
    }

    @Override
    public List<EntryPoint> detectEntryPoints(ClassOrInterfaceDeclaration clazz) {
        List<EntryPoint> entryPoints = new ArrayList<>();
        if (!hasAny(clazz, "RestController", "Controller")) {
            return entryPoints;
        }

        String classPrefix = annotation(clazz, "RequestMapping")
                .flatMap(a -> AnnotationUtils.extractStringValue(a, "value", "path"))
                .orElse("");

        for (MethodDeclaration method : clazz.getMethods()) {
            boolean matched = false;
            for (Map.Entry<String, String> verb : VERB_ANNOTATIONS.entrySet()) {
                Optional<AnnotationExpr> ann = annotation(method, verb.getKey());
                if (ann.isPresent()) {
                    matched = true;
                    String path = AnnotationUtils.extractStringValue(ann.get(), "value", "path").orElse("");
                    entryPoints.add(new EntryPoint(verb.getValue(), AnnotationUtils.joinPaths(classPrefix, path), method));
                }
            }
            if (!matched) {
                Optional<AnnotationExpr> ann = annotation(method, "RequestMapping");
                if (ann.isPresent()) {
                    String path = AnnotationUtils.extractStringValue(ann.get(), "value", "path").orElse("");
                    entryPoints.add(new EntryPoint(verbOf(ann.get()), AnnotationUtils.joinPaths(classPrefix, path), method));
                }
            }
        }
        return entryPoints;
    }

    @Override
    public NodeType classify(ClassOrInterfaceDeclaration clazz) {
        if (hasAny(clazz, "FeignClient")) return NodeType.CLIENT;
        if (hasAny(clazz, "RestController", "Controller")) return NodeType.CONTROLLER;
        if (hasAny(clazz, "Service")) return NodeType.SERVICE;
        if (hasAny(clazz, "Repository")) return NodeType.REPOSITORY;
        if (hasAny(clazz, "Component")) return NodeType.COMPONENT;
        return NodeType.OTHER;
    }

    /** @RequestMapping(method = RequestMethod.POST) -> POST; no method given -> GET. */
    private String verbOf(AnnotationExpr ann) {
        if (ann.isNormalAnnotationExpr()) {
            for (MemberValuePair pair : ann.asNormalAnnotationExpr().getPairs()) {
                if (pair.getNameAsString().equals("method")) {
                    Matcher m = VERB.matcher(pair.getValue().toString());
                    if (m.find()) return m.group();
                }
            }
        }
        return "GET";
    }

    private boolean hasAny(NodeWithAnnotations<?> node, String... names) {
        for (String n : names) {
            if (annotation(node, n).isPresent()) return true;
        }
        return false;
    }

    private Optional<AnnotationExpr> annotation(NodeWithAnnotations<?> node, String name) {
        return node.getAnnotations().stream().filter(a -> a.getNameAsString().equals(name)).findFirst();
    }
}
