package io.github.chonghan.javareferenceindex.model;

import java.nio.file.Path;
import java.util.Objects;

public record SourceReference(
    String qualifiedName,
    Path sourceFile,
    ProjectCoordinates targetProject,
    SourceSetCoordinates targetSourceSet
) {
    public SourceReference {
        Objects.requireNonNull(qualifiedName, "qualifiedName");
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(targetProject, "targetProject");
        Objects.requireNonNull(targetSourceSet, "targetSourceSet");
    }
}
