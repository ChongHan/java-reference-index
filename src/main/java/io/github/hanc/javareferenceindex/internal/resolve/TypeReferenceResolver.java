package io.github.hanc.javareferenceindex.internal.resolve;

import io.github.hanc.javareferenceindex.model.BinaryReference;
import io.github.hanc.javareferenceindex.model.ProjectIndexingRequest;
import io.github.hanc.javareferenceindex.model.SourceReference;
import java.nio.file.Path;
import java.util.Optional;

public interface TypeReferenceResolver {
    Optional<SourceReference> resolveSource(String qualifiedName, Path sourceFile, ProjectIndexingRequest request);

    Optional<BinaryReference> resolveBinary(String qualifiedName, ProjectIndexingRequest request);
}
