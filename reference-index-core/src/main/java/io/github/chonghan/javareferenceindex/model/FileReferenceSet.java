package io.github.chonghan.javareferenceindex.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record FileReferenceSet(
    Path sourceFile,
    List<SourceReference> sourceReferences,
    List<BinaryReference> binaryReferences,
    List<UnresolvedReference> unresolvedReferences
) {
    public FileReferenceSet {
        Objects.requireNonNull(sourceFile, "sourceFile");
        sourceReferences = List.copyOf(Objects.requireNonNull(sourceReferences, "sourceReferences"));
        binaryReferences = List.copyOf(Objects.requireNonNull(binaryReferences, "binaryReferences"));
        unresolvedReferences = List.copyOf(Objects.requireNonNull(unresolvedReferences, "unresolvedReferences"));
    }
}
