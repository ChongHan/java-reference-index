package io.github.chonghan.javareferenceindex.model;

import java.nio.file.Path;

public record SourceReference(
    String qualifiedName,
    Path sourceFile,
    ProjectCoordinates targetProject,
    SourceSetCoordinates targetSourceSet
) {}
