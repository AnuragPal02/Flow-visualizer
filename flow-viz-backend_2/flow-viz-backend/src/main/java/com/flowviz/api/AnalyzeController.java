package com.flowviz.api;

import com.flowviz.parser.RepoAnalyzer;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

@RestController
@CrossOrigin(origins = "*") // local demo tool; lock this down before it is anything more
public class AnalyzeController {

    private final RepoAnalyzer repoAnalyzer = new RepoAnalyzer();

    @PostMapping("/analyze")
    public AnalyzeResponse analyze(@RequestBody AnalyzeRequest request) {
        // Windows "Copy as path" wraps the path in quotes
        String raw = request.getSourcePath() == null ? "" : request.getSourcePath().trim().replaceAll("^\"|\"$", "");
        if (raw.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the path to the repository's src/main/java folder.");
        }

        Path path;
        try {
            path = Path.of(raw);
        } catch (InvalidPathException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a valid path: " + raw);
        }
        if (!Files.isDirectory(path)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder not found: " + raw);
        }

        RepoAnalyzer.AnalysisResult result = repoAnalyzer.analyze(path);
        return new AnalyzeResponse(raw, result.getEndpoints(), result.getWarnings(), result.getSources(),
                result.getClassCount());
    }
}
