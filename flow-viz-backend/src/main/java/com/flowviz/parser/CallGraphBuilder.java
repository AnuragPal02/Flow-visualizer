package com.flowviz.parser;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.flowviz.adapter.AdapterRegistry;
import com.flowviz.adapter.FrameworkAdapter;
import com.flowviz.model.EndpointGraph;
import com.flowviz.model.GraphEdge;
import com.flowviz.model.GraphNode;
import com.flowviz.model.NodeType;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Walks the call graph starting from a single entry-point method, resolving
 * each method call via the symbol solver and recursing into in-repo targets.
 * Stops recursing at REPOSITORY/EXTERNAL_CALL boundaries and on cycles.
 */
public class CallGraphBuilder {

    // Pure stream/Optional plumbing only. Deliberately excludes generic names like add/put/get,
    // because an unresolved add() on a library type is exactly the "classpath is missing" signal.
    private static final Set<String> STREAM_NOISE = Set.of(
            "stream", "filter", "map", "toList", "collect", "forEach", "anyMatch", "allMatch",
            "noneMatch", "findFirst", "findAny", "orElse", "orElseThrow", "ifPresent", "flatMap",
            "sorted", "distinct", "limit", "skip", "reduce");

    private final AdapterRegistry adapterRegistry;
    private final Map<String, ClassOrInterfaceDeclaration> classesByQualifiedName;
    private final Map<String, String> fileByQualifiedName;
    private final List<String> warnings;

    public CallGraphBuilder(AdapterRegistry adapterRegistry,
                            Map<String, ClassOrInterfaceDeclaration> classesByQualifiedName,
                            Map<String, String> fileByQualifiedName,
                            List<String> warnings) {
        this.adapterRegistry = adapterRegistry;
        this.classesByQualifiedName = classesByQualifiedName;
        this.fileByQualifiedName = fileByQualifiedName;
        this.warnings = warnings;
    }

    public EndpointGraph build(EntryPoint entryPoint, ClassOrInterfaceDeclaration entryClass,
                               MethodDeclaration entryMethod, String entryFile, FrameworkAdapter entryAdapter) {

        EndpointGraph graph = new EndpointGraph();
        graph.setHttpMethod(entryPoint.getHttpMethod());
        graph.setPath(entryPoint.getPath());
        graph.setFramework(entryAdapter.frameworkName());

        String rootId = nodeId(entryClass.getNameAsString(), entryMethod.getNameAsString());
        graph.addNode(new GraphNode(rootId, entryClass.getNameAsString(), entryMethod.getNameAsString(),
                entryFile, NodeType.CONTROLLER, entryAdapter.frameworkName()));

        Set<String> visited = new HashSet<>();
        visited.add(rootId);
        walk(entryMethod, rootId, graph, visited);
        return graph;
    }

    private void walk(MethodDeclaration method, String callerId, EndpointGraph graph, Set<String> visited) {
        List<MethodCallExpr> calls = method.findAll(MethodCallExpr.class);

        for (MethodCallExpr call : calls) {
            try {
                ResolvedMethodDeclaration resolved = call.resolve();
                String declaringType = resolved.declaringType().getQualifiedName();
                String calledMethodName = resolved.getName();

                if (classesByQualifiedName.containsKey(declaringType)) {
                    handleInRepoCall(declaringType, calledMethodName, callerId, graph, visited);
                } else {
                    handleExternalCall(declaringType, calledMethodName, callerId, graph);
                }
            } catch (UnsolvedSymbolException e) {
                // JavaParser's resolver struggles with lambda type inference inside stream
                // chains (.filter(x -> ...).map(...).toList()). Those calls are JDK plumbing
                // that would be dropped as noise anyway, so don't spam the UI about them.
                if (!STREAM_NOISE.contains(call.getNameAsString())) {
                    warnings.add("Could not resolve call '" + call.getNameAsString() + "' in "
                            + callerId + " — left as unresolved. If this is happening a lot, "
                            + "the classpath jars probably aren't being loaded correctly.");
                }
            } catch (Exception e) {
                // JavaParser's resolver can throw a handful of runtime exceptions beyond
                // UnsolvedSymbolException on edge-case generics/overload resolution —
                // never let one bad call abort the whole walk for an endpoint.
                warnings.add("Resolution error on call '" + call.getNameAsString() + "' in "
                        + callerId + ": " + e.getClass().getSimpleName());
            }
        }
    }

    private void handleInRepoCall(String declaringType, String calledMethodName, String callerId,
                                  EndpointGraph graph, Set<String> visited) {
        ClassOrInterfaceDeclaration targetClass = classesByQualifiedName.get(declaringType);
        String targetId = nodeId(targetClass.getNameAsString(), calledMethodName);

        Optional<FrameworkAdapter> targetAdapter = adapterRegistry.resolve(targetClass);
        NodeType targetType = targetAdapter.map(a -> a.classify(targetClass)).orElse(NodeType.COMPONENT);
        String targetFramework = targetAdapter.map(FrameworkAdapter::frameworkName).orElse("unknown");
        String targetFile = fileByQualifiedName.getOrDefault(declaringType, targetClass.getNameAsString() + ".java");

        // getSourcePath()/setName()/isActive() on a plain DTO/model class is pure clutter in a
        // flow diagram. Only skip when NO framework adapter recognized the class, so a
        // @Service/@Component/@Repository with a getX() method still shows up.
        if (targetAdapter.isEmpty() && isTrivialAccessor(calledMethodName)) {
            return;
        }

        if (!graph.containsNode(targetId)) {
            graph.addNode(new GraphNode(targetId, targetClass.getNameAsString(), calledMethodName,
                    targetFile, targetType, targetFramework));
        }
        graph.addEdge(new GraphEdge(callerId, targetId));

        boolean isBoundary = targetType == NodeType.REPOSITORY || targetType == NodeType.EXTERNAL_CALL;
        if (!visited.contains(targetId) && !isBoundary) {
            visited.add(targetId);
            targetClass.getMethods().stream()
                    .filter(m -> m.getNameAsString().equals(calledMethodName))
                    .findFirst()
                    .ifPresent(targetMethod -> walk(targetMethod, targetId, graph, visited));
        }
    }

    private void handleExternalCall(String declaringType, String calledMethodName, String callerId, EndpointGraph graph) {
        if (isNoiseLibraryCall(declaringType)) {
            return; // don't graph every java.util.List.get()/toString()/equals() call — pure noise
        }

        boolean isExternalClient = adapterRegistry.all().stream()
                .anyMatch(a -> a.isExternalCallType(declaringType));
        NodeType terminalType = isExternalClient ? NodeType.EXTERNAL_CALL : NodeType.LIBRARY;

        String shortClassName = shortName(declaringType);
        String targetId = nodeId(shortClassName, calledMethodName);

        if (!graph.containsNode(targetId)) {
            graph.addNode(new GraphNode(targetId, shortClassName, calledMethodName,
                    declaringType, terminalType, "external"));
        }
        graph.addEdge(new GraphEdge(callerId, targetId));
    }

    private boolean isTrivialAccessor(String methodName) {
        return methodName.matches("^(get|set|is)[A-Z].*");
    }

    private boolean isNoiseLibraryCall(String qualifiedType) {
        return qualifiedType.startsWith("java.lang.")
                || qualifiedType.startsWith("java.util.")
                || qualifiedType.startsWith("java.io.")
                || qualifiedType.equals("java.lang.Object");
    }

    private String shortName(String qualifiedType) {
        int idx = qualifiedType.lastIndexOf('.');
        return idx >= 0 ? qualifiedType.substring(idx + 1) : qualifiedType;
    }

    private String nodeId(String className, String methodName) {
        return className + "." + methodName;
    }
}