package io.github.hanc.javareferenceindex.internal.resolve;

import io.github.hanc.javareferenceindex.model.BinaryReference;
import io.github.hanc.javareferenceindex.model.ProjectIndexingRequest;
import io.github.hanc.javareferenceindex.model.SourceReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class SourceAndClasspathTypeReferenceResolver implements TypeReferenceResolver {
    @Override
    public Optional<SourceReference> resolveSource(String qualifiedName, Path sourceFile, ProjectIndexingRequest request) {
        return sourcePathFor(qualifiedName, request)
            .filter(sourcePath -> !sourcePath.equals(sourceFile.toAbsolutePath().normalize()))
            .map(sourcePath -> new SourceReference(qualifiedName, sourcePath));
    }

    @Override
    public Optional<BinaryReference> resolveBinary(String qualifiedName, ProjectIndexingRequest request) {
        return binaryPathFor(qualifiedName, request)
            .map(binaryPath -> new BinaryReference(qualifiedName, binaryPath));
    }

    private static Optional<Path> sourcePathFor(String qualifiedName, ProjectIndexingRequest request) {
        for (var sourceRoot : request.sourceRoots()) {
            Optional<Path> sourcePath = sourcePathFor(qualifiedName, sourceRoot.path());
            if (sourcePath.isPresent()) {
                return sourcePath;
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> sourcePathFor(String qualifiedName, Path sourceRoot) {
        String candidateName = qualifiedName;
        while (candidateName.contains(".")) {
            Path candidate = sourceRoot.resolve(candidateName.replace('.', '/') + ".java").toAbsolutePath().normalize();
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
            candidateName = candidateName.substring(0, candidateName.lastIndexOf('.'));
        }
        return Optional.empty();
    }

    private static Optional<Path> binaryPathFor(String qualifiedName, ProjectIndexingRequest request) {
        String classFile = qualifiedName.replace('.', '/') + ".class";
        for (Path classpathEntry : request.classpathEntries()) {
            Path normalized = classpathEntry.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized)) {
                return Optional.of(normalized);
            }
            if (Files.isDirectory(normalized) && Files.isRegularFile(normalized.resolve(classFile))) {
                return Optional.of(normalized);
            }
        }
        return Optional.empty();
    }
}
