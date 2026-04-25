package io.github.hanc.javareferenceindex.model;

import java.nio.file.Path;

public record BinaryReference(
    String qualifiedName,
    Path classpathEntry
) {}
