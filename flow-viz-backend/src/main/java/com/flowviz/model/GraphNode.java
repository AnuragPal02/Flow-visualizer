package com.flowviz.model;

import java.util.Objects;

public class GraphNode {

    private String id; // "ClassName.methodName" — unique within one EndpointGraph
    private String className;
    private String methodName;
    private String file;
    private NodeType type;
    private String framework; // "spring" | "quarkus" | "external" | "unknown"
    private String summary;   // filled in later by the (optional) LLM enrichment step; null until then

    public GraphNode() {
    }

    public GraphNode(String id, String className, String methodName, String file, NodeType type, String framework) {
        this.id = id;
        this.className = className;
        this.methodName = methodName;
        this.file = file;
        this.type = type;
        this.framework = framework;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public NodeType getType() {
        return type;
    }

    public void setType(NodeType type) {
        this.type = type;
    }

    public String getFramework() {
        return framework;
    }

    public void setFramework(String framework) {
        this.framework = framework;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GraphNode)) return false;
        return Objects.equals(id, ((GraphNode) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
