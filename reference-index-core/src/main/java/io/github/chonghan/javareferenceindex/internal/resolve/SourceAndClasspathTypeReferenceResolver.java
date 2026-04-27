package io.github.chonghan.javareferenceindex.internal.resolve;

import io.github.chonghan.javareferenceindex.model.BinaryReference;
import io.github.chonghan.javareferenceindex.model.ClasspathEntry;
import io.github.chonghan.javareferenceindex.model.ProjectIndexingRequest;
import io.github.chonghan.javareferenceindex.model.SourceReference;
import io.github.chonghan.javareferenceindex.model.SourceRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;

public final class SourceAndClasspathTypeReferenceResolver implements TypeReferenceResolver {
    private final Map<ProjectIndexingRequest, BinaryLookup> binaryLookups = new IdentityHashMap<>();

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
        return binaryLookupFor(request).binaryEntryFor(qualifiedName)
            .map(classpathEntry -> new BinaryReference(qualifiedName, classpathEntry.target(), qualifiedName));
    }

    private BinaryLookup binaryLookupFor(ProjectIndexingRequest request) {
        synchronized (binaryLookups) {
            return binaryLookups.computeIfAbsent(request, BinaryLookup::new);
        }
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

    private static final class BinaryLookup {
        private final Map<String, ClasspathEntry> entriesByClass = new java.util.HashMap<>();

        private BinaryLookup(ProjectIndexingRequest request) {
            request.classpathEntries().forEach(this::index);
        }

        private Optional<ClasspathEntry> binaryEntryFor(String qualifiedName) {
            return Optional.ofNullable(entriesByClass.get(qualifiedName));
        }

        private void index(ClasspathEntry classpathEntry) {
            Path normalized = classpathEntry.path().toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized)) {
                indexJar(normalized, classpathEntry);
            } else if (Files.isDirectory(normalized)) {
                indexDirectory(normalized, classpathEntry);
            }
        }

        private void indexJar(Path jar, ClasspathEntry classpathEntry) {
            try (JarFile jarFile = new JarFile(jar.toFile())) {
                jarFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(java.util.jar.JarEntry::getName)
                    .filter(BinaryLookup::isClassFile)
                    .map(BinaryLookup::className)
                    .forEach(className -> entriesByClass.putIfAbsent(className, classpathEntry));
            } catch (IOException ignored) {
                // Ignore unreadable classpath entries in the same way the previous lookup missed them.
            }
        }

        private void indexDirectory(Path directory, ClasspathEntry classpathEntry) {
            try (var paths = Files.walk(directory)) {
                paths
                    .filter(Files::isRegularFile)
                    .filter(path -> isClassFile(path.getFileName().toString()))
                    .map(path -> directory.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/'))
                    .map(BinaryLookup::className)
                    .forEach(className -> entriesByClass.putIfAbsent(className, classpathEntry));
            } catch (IOException ignored) {
                // Ignore unreadable classpath entries in the same way the previous lookup missed them.
            }
        }

        private static boolean isClassFile(String name) {
            return name.endsWith(".class");
        }

        private static String className(String entryName) {
            return entryName
                .replace('/', '.')
                .replace('$', '.')
                .replaceFirst("\\.class$", "");
        }
    }

    private record ResolvedSource(Path path, SourceRoot sourceRoot) {}
}
