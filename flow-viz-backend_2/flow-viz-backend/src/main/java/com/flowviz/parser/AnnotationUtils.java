package com.flowviz.parser;

import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;

import java.util.Optional;

/**
 * Shared helper for both adapters: pulls a String path/value out of an
 * annotation regardless of which of the three forms it's written in —
 * @Foo("x"), @Foo(value = "x"), @Foo(path = "x") — and joins path segments
 * the way Spring/JAX-RS actually do (no double slashes, no trailing slash).
 */
public final class AnnotationUtils {

    private AnnotationUtils() {
    }

    public static Optional<String> extractStringValue(AnnotationExpr annotation, String... memberNames) {
        if (annotation == null) {
            return Optional.empty();
        }

        if (annotation.isSingleMemberAnnotationExpr()) {
            Expression value = annotation.asSingleMemberAnnotationExpr().getMemberValue();
            return firstStringFromExpression(value);
        }

        if (annotation.isNormalAnnotationExpr()) {
            NormalAnnotationExpr normal = annotation.asNormalAnnotationExpr();
            for (String name : memberNames) {
                for (MemberValuePair pair : normal.getPairs()) {
                    if (pair.getNameAsString().equals(name)) {
                        Optional<String> val = firstStringFromExpression(pair.getValue());
                        if (val.isPresent()) {
                            return val;
                        }
                    }
                }
            }
        }

        // MarkerAnnotationExpr (no parentheses at all, e.g. bare @RequestMapping) has no value.
        return Optional.empty();
    }

    private static Optional<String> firstStringFromExpression(Expression expr) {
        if (expr == null) {
            return Optional.empty();
        }
        if (expr.isStringLiteralExpr()) {
            return Optional.of(expr.asStringLiteralExpr().asString());
        }
        if (expr.isArrayInitializerExpr()) {
            ArrayInitializerExpr array = expr.asArrayInitializerExpr();
            if (!array.getValues().isEmpty() && array.getValues().get(0).isStringLiteralExpr()) {
                return Optional.of(array.getValues().get(0).asStringLiteralExpr().asString());
            }
        }
        return Optional.empty();
    }

    /** Joins a class-level prefix and a method-level path the way Spring/JAX-RS resolve them. */
    public static String joinPaths(String prefix, String suffix) {
        String p = prefix == null ? "" : prefix.trim();
        String s = suffix == null ? "" : suffix.trim();
        String combined = (p + "/" + s).replaceAll("/+", "/");
        if (combined.isEmpty()) {
            combined = "/";
        }
        if (!combined.startsWith("/")) {
            combined = "/" + combined;
        }
        if (combined.length() > 1 && combined.endsWith("/")) {
            combined = combined.substring(0, combined.length() - 1);
        }
        return combined;
    }
}
