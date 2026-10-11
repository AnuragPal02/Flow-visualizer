package com.flowviz.parser;

import com.github.javaparser.ast.body.MethodDeclaration;

/** A REST endpoint, normalized so nothing downstream cares which framework declared it. */
public class EntryPoint {

    private final String httpMethod;
    private final String path;
    private final MethodDeclaration method;

    public EntryPoint(String httpMethod, String path, MethodDeclaration method) {
        this.httpMethod = httpMethod;
        this.path = path;
        this.method = method;
    }

    public String getHttpMethod() { return httpMethod; }
    public String getPath() { return path; }
    public MethodDeclaration getMethod() { return method; }
    public String getMethodName() { return method.getNameAsString(); }
}
