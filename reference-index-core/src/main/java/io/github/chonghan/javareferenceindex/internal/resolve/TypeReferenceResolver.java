package io.github.chonghan.javareferenceindex.internal.resolve;

import io.github.chonghan.javareferenceindex.model.BinaryReference;
import io.github.chonghan.javareferenceindex.model.ProjectIndexingRequest;
import io.github.chonghan.javareferenceindex.model.SourceReference;
import java.nio.file.Path;
import java.util.Optional;

public interface TypeReferenceResolver {
    Optional<SourceReference> resolveSource(String qualifiedName, Path sourceFile, ProjectIndexingRequest request);

    Optional<BinaryReference> resolveBinary(String qualifiedName, ProjectIndexingRequest request);
}
