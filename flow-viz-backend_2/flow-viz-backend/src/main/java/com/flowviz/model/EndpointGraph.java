package com.flowviz.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The application flow for one REST endpoint. */
public class EndpointGraph {

    private String httpMethod;
    private String path;
    private String framework;
    private String returns;                          // declared return type of the endpoint method
    private List<EndpointInput> inputs = new ArrayList<>();
    private EndpointSummary summary;

    private final Map<String, GraphNode> nodesById = new LinkedHashMap<>();
    private final List<GraphEdge> edges = new ArrayList<>();
    private final Set<String> edgeKeys = new HashSet<>();

    public void addNode(GraphNode node) {
        nodesById.putIfAbsent(node.getId(), node);
    }

    public GraphNode node(String id) {
        return nodesById.get(id);
    }

    public boolean containsNode(String id) {
        return nodesById.containsKey(id);
    }

    public int nodeCount() {
        return nodesById.size();
    }

    /** Same caller -> callee pair is only recorded once; the first call site (line + code) is kept. */
    public void addEdge(String from, String to, int line, String code) {
        if (edgeKeys.add(from + "->" + to)) {
            edges.add(new GraphEdge(from, to, line, code));
        }
    }

    public List<GraphNode> getNodes() { return new ArrayList<>(nodesById.values()); }
    public List<GraphEdge> getEdges() { return edges; }
    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getFramework() { return framework; }
    public void setFramework(String framework) { this.framework = framework; }
    public String getReturns() { return returns; }
    public void setReturns(String returns) { this.returns = returns; }
    public List<EndpointInput> getInputs() { return inputs; }
    public void setInputs(List<EndpointInput> inputs) { this.inputs = inputs; }
    public EndpointSummary getSummary() { return summary; }
    public void setSummary(EndpointSummary summary) { this.summary = summary; }
}
