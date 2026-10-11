package com.flowviz.api;

import com.flowviz.model.EndpointGraph;

import java.util.List;
import java.util.Map;

public class AnalyzeResponse {

    private String sourcePath;
    private int endpointsFound;
    private int classesAnalyzed;
    private List<EndpointGraph> endpoints;
    private List<String> warnings;
    private Map<String, String> sources; // file path -> full source, for every file that appears in a flow

    public AnalyzeResponse() {
    }

    public AnalyzeResponse(String sourcePath, List<EndpointGraph> endpoints, List<String> warnings, Map<String, String> sources,
                           int classesAnalyzed) {
        this.sourcePath = sourcePath;
        this.endpoints = endpoints;
        this.endpointsFound = endpoints.size();
        this.warnings = warnings;
        this.sources = sources;
        this.classesAnalyzed = classesAnalyzed;
    }

    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
    public int getClassesAnalyzed() { return classesAnalyzed; }
    public void setClassesAnalyzed(int classesAnalyzed) { this.classesAnalyzed = classesAnalyzed; }
    public int getEndpointsFound() { return endpointsFound; }
    public void setEndpointsFound(int endpointsFound) { this.endpointsFound = endpointsFound; }
    public List<EndpointGraph> getEndpoints() { return endpoints; }
    public void setEndpoints(List<EndpointGraph> endpoints) { this.endpoints = endpoints; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    public Map<String, String> getSources() { return sources; }
    public void setSources(Map<String, String> sources) { this.sources = sources; }
}
