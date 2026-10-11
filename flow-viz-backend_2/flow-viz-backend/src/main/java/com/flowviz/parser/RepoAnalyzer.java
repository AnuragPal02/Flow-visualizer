package com.flowviz.parser;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.utils.SourceRoot;
import com.flowviz.adapter.AdapterRegistry;
import com.flowviz.adapter.FrameworkAdapter;
import com.flowviz.model.EndpointGraph;
import com.flowviz.model.GraphNode;
import com.flowviz.parser.SourceIndex.ClassInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Parses the target repo's own source files, finds every REST endpoint and builds its application flow.
 * No classpath and no dependency jars are involved.
 */
public class RepoAnalyzer {

    private static final int MAX_WARNINGS = 30;

    private final AdapterRegistry registry = new AdapterRegistry();

    /** @param input a src/main/java folder, or a repo/module folder that contains one (or several) */
    public AnalysisResult analyze(Path input) {
        Set<String> warnings = new LinkedHashSet<>();
        Path norm = input.toAbsolutePath().normalize();
        List<Path> roots = discoverSourceRoots(norm);
        Path base = isSrcMainJava(norm) ? norm.getParent().getParent().getParent() : norm;

        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);

        SourceIndex index = new SourceIndex(warnings);
        for (Path root : roots) {
            try {
                for (ParseResult<CompilationUnit> result : new SourceRoot(root, config).tryToParse()) {
                    if (!result.isSuccessful() && !result.getProblems().isEmpty()) {
                        warnings.add("Parsing problem in application source: " + result.getProblems().get(0).getMessage());
                    }
                    result.getResult().ifPresent(cu -> index.addUnit(cu, displayPath(base, cu)));
                }
            } catch (IOException e) {
                warnings.add("Could not read source folder " + root + ": " + e.getMessage());
            }
        }
        if (index.size() == 0) {
            warnings.add("No Java classes found under " + norm);
        }

        List<EndpointGraph> graphs = new ArrayList<>();
        for (ClassInfo info : index.allClasses()) {
            for (FrameworkAdapter adapter : registry.all()) {
                if (!adapter.appliesTo(info.decl)) continue;
                for (EntryPoint entryPoint : adapter.detectEntryPoints(info.decl)) {
                    graphs.add(new CallGraphBuilder(index, registry, warnings).build(entryPoint, info, adapter));
                }
            }
        }
        graphs.sort(Comparator.comparing(EndpointGraph::getPath).thenComparing(EndpointGraph::getHttpMethod));

        // full source of every file that shows up in a flow, sent once so the UI can display class code
        Map<String, String> sources = new LinkedHashMap<>();
        for (EndpointGraph g : graphs) {
            for (GraphNode n : g.getNodes()) {
                sources.computeIfAbsent(n.getFile(), index::source);
            }
        }

        return new AnalysisResult(graphs, cap(warnings), sources, index.size());
    }

    // ------------------------------------------------------------------ finding the source folders

    private static List<Path> discoverSourceRoots(Path norm) {
        if (isSrcMainJava(norm)) return List.of(norm);

        Path direct = norm.resolve("src").resolve("main").resolve("java");
        if (Files.isDirectory(direct)) return List.of(direct);

        List<Path> found = new ArrayList<>(); // multi-module repo: one src/main/java per module
        try (Stream<Path> walk = Files.walk(norm, 6)) {
            walk.filter(p -> isSrcMainJava(p) && Files.isDirectory(p) && !inIgnoredFolder(p)).forEach(found::add);
        } catch (Exception ignored) {
            // unreadable folder: fall through with whatever was found
        }
        found.sort(Comparator.naturalOrder());
        return found.isEmpty() ? List.of(norm) : found;
    }

    private static boolean isSrcMainJava(Path p) {
        int n = p.getNameCount();
        return n >= 3
                && p.getName(n - 1).toString().equalsIgnoreCase("java")
                && p.getName(n - 2).toString().equalsIgnoreCase("main")
                && p.getName(n - 3).toString().equalsIgnoreCase("src");
    }

    private static boolean inIgnoredFolder(Path p) {
        for (Path part : p) {
            String s = part.toString();
            if (s.equals("node_modules") || s.equals("target") || s.equals("build") || s.equals(".git")) return true;
        }
        return false;
    }

    private static String displayPath(Path base, CompilationUnit cu) {
        Path file = cu.getStorage().map(CompilationUnit.Storage::getPath).orElse(null);
        if (file == null) return "unknown";
        Path abs = file.toAbsolutePath().normalize();
        Path shown = abs.startsWith(base) ? base.relativize(abs) : abs;
        return shown.toString().replace('\\', '/');
    }

    private static List<String> cap(Set<String> warnings) {
        List<String> all = new ArrayList<>(warnings);
        if (all.size() <= MAX_WARNINGS) return all;
        List<String> capped = new ArrayList<>(all.subList(0, MAX_WARNINGS));
        capped.add("... and " + (all.size() - MAX_WARNINGS) + " more warnings not shown");
        return capped;
    }

    public static class AnalysisResult {
        private final List<EndpointGraph> endpoints;
        private final List<String> warnings;
        private final Map<String, String> sources;
        private final int classCount;

        public AnalysisResult(List<EndpointGraph> endpoints, List<String> warnings, Map<String, String> sources, int classCount) {
            this.endpoints = endpoints;
            this.warnings = warnings;
            this.sources = sources;
            this.classCount = classCount;
        }

        public List<EndpointGraph> getEndpoints() { return endpoints; }
        public List<String> getWarnings() { return warnings; }
        public Map<String, String> getSources() { return sources; }
        public int getClassCount() { return classCount; }
    }
}
