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
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;

public final class SourceAndClasspathTypeReferenceResolver implements TypeReferenceResolver {
    private final Map<ProjectIndexingRequest, SourceLookup> sourceLookups = new IdentityHashMap<>();
    private final Map<ProjectIndexingRequest, BinaryLookup> binaryLookups = new IdentityHashMap<>();

    @Override
    public Optional<SourceReference> resolveSource(String qualifiedName, Path sourceFile, ProjectIndexingRequest request) {
        Path normalizedSourceFile = sourceFile.toAbsolutePath().normalize();
        return sourceLookupFor(request).sourcePathFor(qualifiedName)
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

    private SourceLookup sourceLookupFor(ProjectIndexingRequest request) {
        synchronized (sourceLookups) {
            return sourceLookups.computeIfAbsent(request, SourceLookup::new);
        }
    }

    private BinaryLookup binaryLookupFor(ProjectIndexingRequest request) {
        synchronized (binaryLookups) {
            return binaryLookups.computeIfAbsent(request, BinaryLookup::new);
        }
    }

    private static final class SourceLookup {
        private final Map<String, ResolvedSource> sourcesByTopLevelType = new java.util.HashMap<>();
        private final ProjectIndexingRequest request;

        private SourceLookup(ProjectIndexingRequest request) {
            this.request = request;
            request.sourceRoots().forEach(this::index);
        }

        private Optional<ResolvedSource> sourcePathFor(String qualifiedName) {
            String candidateName = qualifiedName;
            while (candidateName.contains(".")) {
                ResolvedSource source = sourcesByTopLevelType.get(candidateName);
                if (source != null) {
                    return Optional.of(source);
                }
                candidateName = candidateName.substring(0, candidateName.lastIndexOf('.'));
            }
            return Optional.empty();
        }

        private void index(SourceRoot sourceRoot) {
            Path root = sourceRoot.path().toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) {
                return;
            }
            try (var paths = Files.walk(root)) {
                paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .forEach(path -> {
                        ResolvedSource source = new ResolvedSource(path.toAbsolutePath().normalize(), sourceRoot);
                        if (!indexDeclaredTopLevelTypes(source)) {
                            sourcesByTopLevelType.putIfAbsent(qualifiedName(root, path), source);
                        }
                    });
            } catch (IOException ignored) {
                // Ignore unreadable source roots in the same way the previous lookup missed them.
            }
        }

        private boolean indexDeclaredTopLevelTypes(ResolvedSource source) {
            try {
                ASTParser parser = ASTParser.newParser(AST.JLS21);
                parser.setKind(ASTParser.K_COMPILATION_UNIT);
                parser.setSource(Files.readString(source.path(), request.compilerSettings().encoding()).toCharArray());
                Map<String, String> options = JavaCore.getOptions();
                JavaCore.setComplianceOptions(request.compilerSettings().effectiveSourceLevel().compilerLevel(), options);
                parser.setCompilerOptions(options);
                CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
                String packageName = compilationUnit.getPackage() == null
                    ? ""
                    : compilationUnit.getPackage().getName().getFullyQualifiedName();
                boolean foundDeclaration = false;
                for (Object declaration : compilationUnit.types()) {
                    if (declaration instanceof AbstractTypeDeclaration typeDeclaration) {
                        foundDeclaration = true;
                        String simpleName = typeDeclaration.getName().getIdentifier();
                        String qualifiedName = packageName.isBlank() ? simpleName : packageName + "." + simpleName;
                        sourcesByTopLevelType.putIfAbsent(qualifiedName, source);
                    }
                }
                return foundDeclaration;
            } catch (IOException ignored) {
                return false;
            }
        }

        private static String qualifiedName(Path root, Path sourceFile) {
            return root.relativize(sourceFile.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/')
                .replace('/', '.')
                .replaceFirst("\\.java$", "");
        }
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
            try (JarFile jarFile = new JarFile(jar.toFile(), true, JarFile.OPEN_READ, Runtime.version())) {
                jarFile.versionedStream()
                    .filter(entry -> !entry.isDirectory())
                    .map(java.util.jar.JarEntry::getName)
                    .filter(BinaryLookup::isClassFile)
                    .forEach(entryName -> indexClass(entryName, classpathEntry));
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
                    .forEach(entryName -> indexClass(entryName, classpathEntry));
            } catch (IOException ignored) {
                // Ignore unreadable classpath entries in the same way the previous lookup missed them.
            }
        }

        private void indexClass(String entryName, ClasspathEntry classpathEntry) {
            String binaryName = binaryClassName(entryName);
            entriesByClass.putIfAbsent(binaryName, classpathEntry);
            entriesByClass.putIfAbsent(binaryName.replace('$', '.'), classpathEntry);
        }

        private static boolean isClassFile(String name) {
            return name.endsWith(".class");
        }

        private static String binaryClassName(String entryName) {
            return entryName
                .replace('/', '.')
                .replaceFirst("\\.class$", "");
        }
    }

    private record ResolvedSource(Path path, SourceRoot sourceRoot) {}
}
