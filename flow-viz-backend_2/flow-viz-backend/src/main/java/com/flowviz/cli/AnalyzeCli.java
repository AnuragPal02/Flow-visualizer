package com.flowviz.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.flowviz.model.EndpointGraph;
import com.flowviz.model.GraphEdge;
import com.flowviz.model.GraphNode;
import com.flowviz.parser.RepoAnalyzer;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Quick check without the UI: prints every endpoint's flow as a tree and optionally writes the JSON.
 *
 *   mvn compile exec:java -Dexec.args="C:\path\to\repo\src\main\java [graph.json]"
 */
public class AnalyzeCli {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: AnalyzeCli <path-to-src/main/java> [output.json]");
            System.exit(1);
        }

        RepoAnalyzer.AnalysisResult result = new RepoAnalyzer().analyze(Path.of(args[0]));

        System.out.println("Found " + result.getEndpoints().size() + " endpoint(s)\n");
        for (EndpointGraph g : result.getEndpoints()) {
            print(g);
        }
        result.getWarnings().forEach(w -> System.out.println("WARNING: " + w));

        if (args.length > 1) {
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(new File(args[1]), result.getEndpoints());
            System.out.println("\nWrote " + args[1]);
        }
    }

    private static void print(EndpointGraph g) {
        System.out.println(g.getHttpMethod() + " " + g.getPath());

        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        g.getNodes().forEach(n -> nodes.put(n.getId(), n));
        Map<String, List<GraphEdge>> children = new LinkedHashMap<>();
        for (GraphEdge e : g.getEdges()) {
            children.computeIfAbsent(e.getFrom(), k -> new ArrayList<>()).add(e);
        }
        walk(g.getNodes().get(0).getId(), null, 1, nodes, children, new HashSet<>());
        System.out.println();
    }

    private static void walk(String id, GraphEdge via, int depth, Map<String, GraphNode> nodes,
                             Map<String, List<GraphEdge>> children, Set<String> printed) {
        GraphNode n = nodes.get(id);
        String label = n.getClassName() + "." + n.getMethodName() + "()  [" + n.getType() + "]";
        String at = via != null && via.getLine() > 0 ? "   <- line " + via.getLine() + ": " + via.getCode() : "";
        boolean again = !printed.add(id);
        System.out.println("  ".repeat(depth) + "-> " + label + at + (again ? "  (shown above)" : ""));
        if (again) return;
        for (GraphEdge e : children.getOrDefault(id, List.of())) {
            walk(e.getTo(), e, depth + 1, nodes, children, printed);
        }
    }
}
