package io.github.hanc.javareferenceindex.model;

import java.nio.file.Path;
import java.util.List;

public record ProjectIndexingRequest(
    ProjectCoordinates project,
    SourceSetCoordinates sourceSet,
    List<SourceRoot> sourceRoots,
    List<Path> sourceFiles,
    List<Path> classpathEntries,
    JavaCompilerSettings compilerSettings
) {
    public ProjectIndexingRequest {
        sourceRoots = List.copyOf(sourceRoots);
        sourceFiles = List.copyOf(sourceFiles);
        classpathEntries = List.copyOf(classpathEntries);
    }
}
