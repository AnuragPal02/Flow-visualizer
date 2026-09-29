package com.flowviz.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.flowviz.parser.RepoAnalyzer;
import com.flowviz.parser.TypeSolverFactory;

import java.io.File;
import java.util.List;

/**
 * Standalone CLI for testing the analyzer against a real repo without needing
 * the Spring Boot API up. This is the fast feedback loop for validating symbol
 * resolution — run this FIRST against your target repo before touching the
 * frontend at all.
 *
 * Usage:
 *   mvn compile exec:java -Dexec.mainClass=com.flowviz.cli.AnalyzeCli \
 *     -Dexec.args="<src-main-java-path> <classpath-string> <output.json>"
 *
 * The classpath string is obtained by running, inside the TARGET repo:
 *   mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
 * then passing the contents of cp.txt (quoted) as the second argument here.
 */
public class AnalyzeCli {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: AnalyzeCli <src-main-java-path> <classpath-string> <output.json>");
            System.exit(1);
        }

        File sourceRoot = new File(args[0]);
        String classpathString = args[1];
        File outputFile = new File(args[2]);

        if (!sourceRoot.exists()) {
            System.err.println("Source root does not exist: " + sourceRoot.getAbsolutePath());
            System.exit(1);
        }

        List<File> classpathJars = TypeSolverFactory.parseClasspathString(classpathString);
        System.out.println("[flow-viz] Loaded " + classpathJars.size() + " classpath jars for symbol resolution");

        RepoAnalyzer analyzer = new RepoAnalyzer();
        RepoAnalyzer.AnalysisResult result = analyzer.analyze(sourceRoot, classpathJars);

        System.out.println("[flow-viz] Found " + result.getEndpoints().size() + " endpoint(s)");
        result.getWarnings().forEach(w -> System.out.println("[flow-viz] WARNING: " + w));

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(outputFile, result.getEndpoints());

        System.out.println("[flow-viz] Wrote graph JSON to " + outputFile.getAbsolutePath());
    }
}
