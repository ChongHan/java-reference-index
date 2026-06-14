package io.github.chonghan.javareferenceindex.model;

/**
 * A reference to a type resolved from a binary classpath entry.
 *
 * <p>{@code targetProject} identifies the owning classpath entry, such as a Gradle-resolved
 * {@code group:name:version} coordinate when available. {@code referenceSymbol} is the referenced Java
 * symbol name, for example {@code org.agrona.collections.IntArrayList}.
 */
public record BinaryReference(
    String qualifiedName,
    String targetProject,
    String referenceSymbol
) {}
