package com.flowviz.api;

public class AnalyzeRequest {

    /** Path to the target repo's src/main/java (a repo or module folder containing one also works). */
    private String sourcePath;

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }
}
