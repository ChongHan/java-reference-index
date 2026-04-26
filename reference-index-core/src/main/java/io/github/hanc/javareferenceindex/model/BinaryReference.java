package io.github.hanc.javareferenceindex.model;

/**
 * A reference to a type resolved from a binary classpath entry.
 *
 * <p>{@code targetProject} identifies the owning classpath entry, such as a Gradle-resolved
 * {@code group:name:version} coordinate when available. {@code target} is the referenced Java type
 * name, for example {@code org.agrona.collections.IntArrayList}.
 */
public record BinaryReference(
    String qualifiedName,
    String targetProject,
    String target
) {}
