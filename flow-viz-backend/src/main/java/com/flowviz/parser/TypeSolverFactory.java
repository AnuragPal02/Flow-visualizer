package com.flowviz.parser;

import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * This is the highest-risk piece of the whole project (see: every prior
 * conversation about this build). If call.resolve() is failing on real
 * in-repo or library calls, the problem is almost always here — either the
 * classpath jars weren't passed in, or a jar path doesn't actually exist.
 */
public final class TypeSolverFactory {

    private TypeSolverFactory() {
    }

    /**
     * Builds a CombinedTypeSolver covering: the JDK, the target repo's own
     * source tree, and every jar on the supplied classpath (typically obtained
     * by running `mvn dependency:build-classpath` inside the TARGET repo —
     * not this project).
     */
    public static CombinedTypeSolver build(File sourceRoot, List<File> classpathJars) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(sourceRoot));

        for (File jar : classpathJars) {
            try {
                typeSolver.add(new JarTypeSolver(jar));
            } catch (IOException e) {
                System.err.println("[flow-viz] WARNING: could not load jar for symbol resolution: "
                        + jar.getAbsolutePath() + " (" + e.getMessage() + ")");
            }
        }

        return typeSolver;
    }

    /** Parses a classpath string (as produced by `mvn dependency:build-classpath`) into jar Files. */
    public static List<File> parseClasspathString(String classpath) {
        if (classpath == null || classpath.isBlank()) {
            return List.of();
        }
        String separator = System.getProperty("path.separator"); // ':' on unix, ';' on windows
        return Arrays.stream(classpath.split(separator))
                .map(String::trim) // cp.txt usually ends with a newline — without this the last jar silently drops
                .filter(s -> !s.isBlank())
                .map(File::new)
                .filter(File::exists)
                .toList();
    }
}