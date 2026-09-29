package com.flowviz.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The full call graph for one discovered HTTP endpoint (one root node =
 * the controller/resource method, plus everything reachable from it).
 * This is what gets serialized straight to the frontend as one entry in
 * the /analyze response.
 */
public class EndpointGraph {

    private String httpMethod;
    private String path;
    private String framework;

    // LinkedHashMap so nodes serialize in discovery order — makes debugging
    // the raw JSON far easier than an unordered Set would.
    private final Map<String, GraphNode> nodesById = new LinkedHashMap<>();
    private final List<GraphEdge> edges = new ArrayList<>();

    public void addNode(GraphNode node) {
        nodesById.putIfAbsent(node.getId(), node);
    }

    public boolean containsNode(String id) {
        return nodesById.containsKey(id);
    }

    public void addEdge(GraphEdge edge) {
        edges.add(edge);
    }

    public List<GraphNode> getNodes() {
        return new ArrayList<>(nodesById.values());
    }

    public List<GraphEdge> getEdges() {
        return edges;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getFramework() {
        return framework;
    }

    public void setFramework(String framework) {
        this.framework = framework;
    }
}
