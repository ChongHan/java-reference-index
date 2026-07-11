package io.github.chonghan.javareferenceindex.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record ProjectIndexingRequest(
    ProjectCoordinates project,
    SourceSetCoordinates sourceSet,
    List<SourceRoot> sourceRoots,
    List<Path> sourceFiles,
    List<ClasspathEntry> classpathEntries,
    JavaCompilerSettings compilerSettings
) {
    public ProjectIndexingRequest {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(sourceSet, "sourceSet");
        Objects.requireNonNull(compilerSettings, "compilerSettings");
        sourceRoots = List.copyOf(Objects.requireNonNull(sourceRoots, "sourceRoots"));
        sourceFiles = List.copyOf(Objects.requireNonNull(sourceFiles, "sourceFiles"));
        classpathEntries = List.copyOf(Objects.requireNonNull(classpathEntries, "classpathEntries"));
    }
}
