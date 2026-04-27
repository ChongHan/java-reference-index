package io.github.chonghan.javareferenceindex.model;

import java.nio.file.Path;
import java.util.List;

public record FileReferenceSet(
    Path sourceFile,
    List<SourceReference> sourceReferences,
    List<BinaryReference> binaryReferences,
    List<UnresolvedReference> unresolvedReferences
) {
    public FileReferenceSet {
        sourceReferences = List.copyOf(sourceReferences);
        binaryReferences = List.copyOf(binaryReferences);
        unresolvedReferences = List.copyOf(unresolvedReferences);
    }
}
