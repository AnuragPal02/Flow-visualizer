package com.flowviz.adapter;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.flowviz.model.NodeType;
import com.flowviz.parser.EntryPoint;

import java.util.List;

/**
 * Framework-specific knowledge, used only to (a) find REST entry points and
 * (b) classify the application's own classes. Framework classes themselves never become graph nodes.
 */
public interface FrameworkAdapter {

    String frameworkName();

    /** Does this class carry an annotation/supertype this framework recognizes? */
    boolean appliesTo(ClassOrInterfaceDeclaration clazz);

    /** REST endpoints declared on this class (empty for services, repositories, ...). */
    List<EntryPoint> detectEntryPoints(ClassOrInterfaceDeclaration clazz);

    /** Annotation-based classification; OTHER when the annotations say nothing. */
    NodeType classify(ClassOrInterfaceDeclaration clazz);
}
