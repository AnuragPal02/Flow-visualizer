package com.flowviz.api;

import com.flowviz.parser.RepoAnalyzer;
import com.flowviz.parser.TypeSolverFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.List;

@RestController
@CrossOrigin(origins = "*") // fine for a hackathon demo hitting this from localhost:5173 — lock down before it's anything more
public class AnalyzeController {

    private final RepoAnalyzer repoAnalyzer = new RepoAnalyzer();

    @PostMapping("/analyze")
    public AnalyzeResponse analyze(@RequestBody AnalyzeRequest request) {
        File sourceRoot = new File(request.getSourcePath());
        if (!sourceRoot.exists()) {
            throw new IllegalArgumentException("sourcePath does not exist: " + request.getSourcePath());
        }

        List<File> classpathJars = TypeSolverFactory.parseClasspathString(request.getClasspath());
        RepoAnalyzer.AnalysisResult result = repoAnalyzer.analyze(sourceRoot, classpathJars);

        return new AnalyzeResponse(request.getSourcePath(), result.getEndpoints(), result.getWarnings());
    }
}
