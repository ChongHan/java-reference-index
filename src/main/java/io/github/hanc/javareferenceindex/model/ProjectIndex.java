package io.github.hanc.javareferenceindex.model;

import java.util.List;

public record ProjectIndex(
    ProjectCoordinates project,
    SourceSetCoordinates sourceSet,
    List<FileReferenceSet> files
) {
    public ProjectIndex {
        files = List.copyOf(files);
    }
}
