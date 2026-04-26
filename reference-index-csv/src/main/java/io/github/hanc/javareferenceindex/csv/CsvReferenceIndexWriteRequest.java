package io.github.hanc.javareferenceindex.csv;

import java.nio.file.Path;
import java.util.List;

public record CsvReferenceIndexWriteRequest(
    Path outputFile,
    List<ProjectDirectory> projectDirectories
) {
    public CsvReferenceIndexWriteRequest {
        projectDirectories = List.copyOf(projectDirectories);
    }
}
