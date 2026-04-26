package io.github.hanc.javareferenceindex.internal.resolve;

import io.github.hanc.javareferenceindex.model.BinaryReference;
import io.github.hanc.javareferenceindex.model.ClasspathEntry;
import io.github.hanc.javareferenceindex.model.ProjectIndexingRequest;
import io.github.hanc.javareferenceindex.model.SourceReference;
import io.github.hanc.javareferenceindex.model.SourceRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.jar.JarFile;

public final class SourceAndClasspathTypeReferenceResolver implements TypeReferenceResolver {
    @Override
    public Optional<SourceReference> resolveSource(String qualifiedName, Path sourceFile, ProjectIndexingRequest request) {
        Path normalizedSourceFile = sourceFile.toAbsolutePath().normalize();
        return sourcePathFor(qualifiedName, request)
            .filter(source -> !source.path().equals(normalizedSourceFile))
            .map(source -> new SourceReference(
                qualifiedName,
                source.path(),
                source.sourceRoot().project(),
                source.sourceRoot().sourceSet()
            ));
    }

    @Override
    public Optional<BinaryReference> resolveBinary(String qualifiedName, ProjectIndexingRequest request) {
        return binaryEntryFor(qualifiedName, request)
            .map(classpathEntry -> new BinaryReference(qualifiedName, classpathEntry.target(), qualifiedName));
    }

    private static Optional<ResolvedSource> sourcePathFor(String qualifiedName, ProjectIndexingRequest request) {
        for (var sourceRoot : request.sourceRoots()) {
            Optional<Path> sourcePath = sourcePathFor(qualifiedName, sourceRoot);
            if (sourcePath.isPresent()) {
                return sourcePath.map(path -> new ResolvedSource(path, sourceRoot));
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> sourcePathFor(String qualifiedName, SourceRoot sourceRoot) {
        String candidateName = qualifiedName;
        while (candidateName.contains(".")) {
            Path candidate = sourceRoot.path().resolve(candidateName.replace('.', '/') + ".java").toAbsolutePath().normalize();
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
            candidateName = candidateName.substring(0, candidateName.lastIndexOf('.'));
        }
        return Optional.empty();
    }

    private static Optional<ClasspathEntry> binaryEntryFor(String qualifiedName, ProjectIndexingRequest request) {
        String classFile = qualifiedName.replace('.', '/') + ".class";
        for (ClasspathEntry classpathEntry : request.classpathEntries()) {
            Path normalized = classpathEntry.path().toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized) && jarContains(normalized, classFile)) {
                return Optional.of(classpathEntry);
            }
            if (Files.isDirectory(normalized) && Files.isRegularFile(normalized.resolve(classFile))) {
                return Optional.of(classpathEntry);
            }
        }
        return Optional.empty();
    }

    private static boolean jarContains(Path path, String classFile) {
        try (JarFile jarFile = new JarFile(path.toFile())) {
            return jarFile.getEntry(classFile) != null;
        } catch (IOException e) {
            return false;
        }
    }

    private record ResolvedSource(Path path, SourceRoot sourceRoot) {}
}
