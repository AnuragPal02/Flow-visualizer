package com.flowviz.parser;

public class EntryPoint {

    private final String httpMethod;
    private final String path;
    private final String className;
    private final String methodName;

    public EntryPoint(String httpMethod, String path, String className, String methodName) {
        this.httpMethod = httpMethod;
        this.path = path;
        this.className = className;
        this.methodName = methodName;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getPath() {
        return path;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }
}
