package com.flowviz.api;

public class AnalyzeRequest {

    /** Absolute path to the TARGET repo's src/main/java directory. */
    private String sourcePath;

    /**
     * Classpath string for the target repo, as produced by running
     * `mvn dependency:build-classpath` inside the TARGET repo (not this project).
     * Optional — without it, only JDK + in-repo symbols resolve, which is enough
     * for in-repo call graphs but every library/framework call (RestTemplate,
     * JpaRepository, etc.) will show up as LIBRARY/UNKNOWN instead of being
     * correctly classified.
     */
    private String classpath;

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getClasspath() {
        return classpath;
    }

    public void setClasspath(String classpath) {
        this.classpath = classpath;
    }
}
