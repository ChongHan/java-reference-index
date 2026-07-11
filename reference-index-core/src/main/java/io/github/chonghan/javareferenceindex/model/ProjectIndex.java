package io.github.chonghan.javareferenceindex.model;

import java.util.List;
import java.util.Objects;

public record ProjectIndex(
    ProjectCoordinates project,
    SourceSetCoordinates sourceSet,
    List<FileReferenceSet> files
) {
    public ProjectIndex {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(sourceSet, "sourceSet");
        files = List.copyOf(Objects.requireNonNull(files, "files"));
    }
}
