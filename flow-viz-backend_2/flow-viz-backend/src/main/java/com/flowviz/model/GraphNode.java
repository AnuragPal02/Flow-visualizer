package com.flowviz.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GraphNode {

    private String id;
    private String className;
    private String methodName;
    private String file;      // relative to the analyzed repo, e.g. src/main/java/com/x/OrderService.java
    private NodeType type;
    private String framework; // "spring" | "quarkus" | null
    private int startLine;    // method range in its file (0 = no method body in the repo, e.g. findById)
    private int endLine;

    private String signature; // Order getOrder(Long id)
    private String doc;       // first sentence of the Javadoc, if any
    private List<Tag> tags = new ArrayList<>();
    private List<Integration> integrations = new ArrayList<>();
    private String entity;    // repositories: the entity type
    private String access;    // repositories: READ | WRITE | null
    private String query;     // repositories: the @Query text, if any
    private List<String> alternatives = new ArrayList<>(); // implementations when the interface call is ambiguous

    public GraphNode() {
    }

    public GraphNode(String id, String className, String methodName, String file, NodeType type, String framework,
                     int startLine, int endLine) {
        this.id = id;
        this.className = className;
        this.methodName = methodName;
        this.file = file;
        this.type = type;
        this.framework = framework;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public String getFile() { return file; }
    public void setFile(String file) { this.file = file; }
    public NodeType getType() { return type; }
    public void setType(NodeType type) { this.type = type; }
    public String getFramework() { return framework; }
    public void setFramework(String framework) { this.framework = framework; }
    public int getStartLine() { return startLine; }
    public void setStartLine(int startLine) { this.startLine = startLine; }
    public int getEndLine() { return endLine; }
    public void setEndLine(int endLine) { this.endLine = endLine; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public String getDoc() { return doc; }
    public void setDoc(String doc) { this.doc = doc; }
    public List<Tag> getTags() { return tags; }
    public void setTags(List<Tag> tags) { this.tags = tags; }
    public List<Integration> getIntegrations() { return integrations; }
    public void setIntegrations(List<Integration> integrations) { this.integrations = integrations; }
    public String getEntity() { return entity; }
    public void setEntity(String entity) { this.entity = entity; }
    public String getAccess() { return access; }
    public void setAccess(String access) { this.access = access; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public List<String> getAlternatives() { return alternatives; }
    public void setAlternatives(List<String> alternatives) { this.alternatives = alternatives; }

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
