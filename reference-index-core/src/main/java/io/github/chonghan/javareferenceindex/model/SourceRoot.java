package io.github.chonghan.javareferenceindex.model;

import java.nio.file.Path;

public record SourceRoot(
    Path path,
    ProjectCoordinates project,
    SourceSetCoordinates sourceSet
) {}
