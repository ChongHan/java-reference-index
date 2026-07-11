package io.github.chonghan.javareferenceindex.model;

import java.nio.file.Path;
import java.util.Objects;

public record SourceRoot(
    Path path,
    ProjectCoordinates project,
    SourceSetCoordinates sourceSet
) {
    public SourceRoot {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(sourceSet, "sourceSet");
    }
}
