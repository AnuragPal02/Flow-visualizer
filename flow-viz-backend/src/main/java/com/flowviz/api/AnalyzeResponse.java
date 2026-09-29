package com.flowviz.api;

import com.flowviz.model.EndpointGraph;

import java.util.List;

public class AnalyzeResponse {

    private String sourcePath;
    private int endpointsFound;
    private List<EndpointGraph> endpoints;
    private List<String> warnings;

    public AnalyzeResponse() {
    }

    public AnalyzeResponse(String sourcePath, List<EndpointGraph> endpoints, List<String> warnings) {
        this.sourcePath = sourcePath;
        this.endpoints = endpoints;
        this.endpointsFound = endpoints.size();
        this.warnings = warnings;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public int getEndpointsFound() {
        return endpointsFound;
    }

    public void setEndpointsFound(int endpointsFound) {
        this.endpointsFound = endpointsFound;
    }

    public List<EndpointGraph> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<EndpointGraph> endpoints) {
        this.endpoints = endpoints;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
