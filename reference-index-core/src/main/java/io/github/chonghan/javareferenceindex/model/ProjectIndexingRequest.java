package io.github.chonghan.javareferenceindex.model;

import java.nio.file.Path;
import java.util.List;

public record ProjectIndexingRequest(
    ProjectCoordinates project,
    SourceSetCoordinates sourceSet,
    List<SourceRoot> sourceRoots,
    List<Path> sourceFiles,
    List<ClasspathEntry> classpathEntries,
    JavaCompilerSettings compilerSettings
) {
    public ProjectIndexingRequest {
        sourceRoots = List.copyOf(sourceRoots);
        sourceFiles = List.copyOf(sourceFiles);
        classpathEntries = List.copyOf(classpathEntries);
    }
}
