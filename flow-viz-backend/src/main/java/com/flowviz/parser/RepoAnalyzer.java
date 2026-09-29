package com.flowviz.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.utils.SourceRoot;
import com.flowviz.adapter.AdapterRegistry;
import com.flowviz.adapter.FrameworkAdapter;
import com.flowviz.model.EndpointGraph;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Top-level orchestrator: parses every .java file under a repo's source root,
 * resolves symbols against the supplied classpath, finds every HTTP entry
 * point across all registered framework adapters, and builds a call graph
 * for each one found.
 */
public class RepoAnalyzer {

    private final AdapterRegistry adapterRegistry = new AdapterRegistry();

    public AnalysisResult analyze(File sourceRoot, List<File> classpathJars) {
        List<String> warnings = new ArrayList<>();

        if (classpathJars.isEmpty()) {
            warnings.add("No classpath jars loaded — calls into libraries/frameworks (Spring, Jackson, "
                    + "JavaParser, etc.) can't be resolved. In-repo flow still works. To fix: run "
                    + "`mvn dependency:build-classpath -Dmdep.outputFile=cp.txt` inside the TARGET repo "
                    + "and paste the contents of cp.txt into the Classpath box.");
        }

        CombinedTypeSolver typeSolver = TypeSolverFactory.build(sourceRoot, classpathJars);
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);

        SourceRoot root = new SourceRoot(sourceRoot.toPath());
        root.getParserConfiguration().setSymbolResolver(symbolSolver);

        List<CompilationUnit> units;

            units = root.tryToParseParallelized().stream()
                    .filter(r -> r.getResult().isPresent())
                    .map(r -> r.getResult().get())
                    .toList();

        // Index every class in the repo by fully qualified name, and remember its source file path.
        Map<String, ClassOrInterfaceDeclaration> classesByQualifiedName = new HashMap<>();
        Map<String, String> fileByQualifiedName = new HashMap<>();

        for (CompilationUnit cu : units) {
            String packageName = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
            String filePath = cu.getStorage().map(s -> s.getPath().toString()).orElse("unknown");

            for (ClassOrInterfaceDeclaration clazz : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                // getFullyQualifiedName() includes the outer class for nested types
                // (e.g. com.flowviz.parser.RepoAnalyzer.AnalysisResult) — building the
                // name by hand from package + simple name drops the outer class and makes
                // every nested class look like an external LIBRARY to the walker.
                String qualifiedName = clazz.getFullyQualifiedName()
                        .orElse(packageName.isEmpty()
                                ? clazz.getNameAsString()
                                : packageName + "." + clazz.getNameAsString());
                classesByQualifiedName.put(qualifiedName, clazz);
                fileByQualifiedName.put(qualifiedName, filePath);
            }
        }

        List<EndpointGraph> endpointGraphs = new ArrayList<>();

        for (Map.Entry<String, ClassOrInterfaceDeclaration> entry : classesByQualifiedName.entrySet()) {
            ClassOrInterfaceDeclaration clazz = entry.getValue();

            List<FrameworkAdapter> matchingAdapters = adapterRegistry.all().stream()
                    .filter(a -> a.appliesTo(clazz))
                    .toList();

            if (matchingAdapters.size() > 1) {
                warnings.add("Class " + clazz.getNameAsString() + " matched more than one framework adapter ("
                        + matchingAdapters.stream().map(FrameworkAdapter::frameworkName).toList()
                        + ") — using '" + matchingAdapters.get(0).frameworkName()
                        + "'. Review manually if this is unexpected.");
            }
            if (matchingAdapters.isEmpty()) {
                continue;
            }

            FrameworkAdapter adapter = matchingAdapters.get(0);
            List<EntryPoint> entryPoints = adapter.detectEntryPoints(clazz);

            for (EntryPoint ep : entryPoints) {
                Optional<MethodDeclaration> entryMethod = clazz.getMethods().stream()
                        .filter(m -> m.getNameAsString().equals(ep.getMethodName()))
                        .findFirst();
                if (entryMethod.isEmpty()) {
                    continue;
                }

                CallGraphBuilder builder = new CallGraphBuilder(
                        adapterRegistry, classesByQualifiedName, fileByQualifiedName, warnings);

                EndpointGraph graph = builder.build(ep, clazz, entryMethod.get(),
                        fileByQualifiedName.getOrDefault(entry.getKey(), "unknown"), adapter);
                endpointGraphs.add(graph);
            }
        }

        return new AnalysisResult(endpointGraphs, dedupeAndCap(warnings, 30));
    }

    /** Same warning repeated across endpoints/call sites is noise — collapse it, and cap the total. */
    private static List<String> dedupeAndCap(List<String> warnings, int max) {
        List<String> unique = new ArrayList<>(new java.util.LinkedHashSet<>(warnings));
        if (unique.size() <= max) {
            return unique;
        }
        List<String> capped = new ArrayList<>(unique.subList(0, max));
        capped.add("... and " + (unique.size() - max) + " more similar warnings not shown");
        return capped;
    }

    public static class AnalysisResult {
        private final List<EndpointGraph> endpoints;
        private final List<String> warnings;

        public AnalysisResult(List<EndpointGraph> endpoints, List<String> warnings) {
            this.endpoints = endpoints;
            this.warnings = warnings;
        }

        public List<EndpointGraph> getEndpoints() {
            return endpoints;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }
}