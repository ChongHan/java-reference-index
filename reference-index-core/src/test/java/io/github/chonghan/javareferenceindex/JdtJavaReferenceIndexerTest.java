package io.github.chonghan.javareferenceindex;

import static org.assertj.core.api.Assertions.assertThat;
import static io.github.chonghan.javareferenceindex.ReferenceIndexingFixtures.classpathJarContaining;
import static io.github.chonghan.javareferenceindex.ReferenceIndexingFixtures.fixturePath;
import static io.github.chonghan.javareferenceindex.ReferenceIndexingFixtures.fixtureSourceRoot;
import static io.github.chonghan.javareferenceindex.ReferenceIndexingFixtures.request;
import static io.github.chonghan.javareferenceindex.ReferenceIndexingFixtures.singleFile;

import io.github.chonghan.javareferenceindex.api.JavaReferenceIndexer;
import io.github.chonghan.javareferenceindex.api.JavaReferenceIndexers;
import io.github.chonghan.javareferenceindex.model.BinaryReference;
import io.github.chonghan.javareferenceindex.model.ClasspathEntry;
import io.github.chonghan.javareferenceindex.model.FileReferenceSet;
import io.github.chonghan.javareferenceindex.model.JavaCompilerSettings;
import io.github.chonghan.javareferenceindex.model.ProjectIndex;
import io.github.chonghan.javareferenceindex.model.ProjectCoordinates;
import io.github.chonghan.javareferenceindex.model.ProjectIndexingRequest;
import io.github.chonghan.javareferenceindex.model.SourceReference;
import io.github.chonghan.javareferenceindex.model.SourceRoot;
import io.github.chonghan.javareferenceindex.model.SourceSetCoordinates;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.agrona.collections.IntArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdtJavaReferenceIndexerTest {
    private final JavaReferenceIndexer indexer = JavaReferenceIndexers.jdt();

    @TempDir
    Path tempDir;

    @Test
    void index_withNoReferences_returnsEmptyReferenceSets() {
        Path sourceRoot = fixtureSourceRoot("jdt-indexer-no-references");
        Path sourceFile = sourceRoot.resolve("example/Plain.java");

        ProjectIndex index = indexer.index(request(sourceRoot, List.of(sourceFile), List.of()));

        FileReferenceSet references = singleFile(index);
        assertThat(references.sourceReferences()).isEmpty();
        assertThat(references.binaryReferences()).isEmpty();
        assertThat(references.unresolvedReferences()).isEmpty();
    }

    @Test
    void index_withSourceReference_resolvesReferencedSourceFile() {
        Path sourceRoot = fixtureSourceRoot("jdt-indexer-source-reference");
        Path sourceFile = sourceRoot.resolve("example/UsesHelper.java");
        Path helperFile = sourceRoot.resolve("example/Helper.java").toAbsolutePath().normalize();

        ProjectIndex index = indexer.index(request(sourceRoot, List.of(sourceFile), List.of()));

        assertThat(singleFile(index).sourceReferences())
            .containsExactly(sourceReference("example.Helper", helperFile));
    }

    @Test
    void index_withSourceReferenceFromAnotherSourceRoot_returnsTargetOwnership() {
        Path fixtureRoot = fixturePath("jdt-indexer-source-root-ownership");
        Path appSourceRoot = fixtureRoot.resolve("app/src/main/java");
        Path libSourceRoot = fixtureRoot.resolve("lib/src/main/java");
        Path sourceFile = appSourceRoot.resolve("app/UsesLibrary.java");
        Path libraryFile = libSourceRoot.resolve("lib/LibraryType.java").toAbsolutePath().normalize();
        ProjectCoordinates app = new ProjectCoordinates(":app");
        SourceSetCoordinates main = new SourceSetCoordinates("main");
        ProjectCoordinates lib = new ProjectCoordinates(":lib");

        ProjectIndex index = indexer.index(new ProjectIndexingRequest(
            app,
            main,
            List.of(
                new SourceRoot(appSourceRoot, app, main),
                new SourceRoot(libSourceRoot, lib, main)
            ),
            List.of(sourceFile),
            List.of(),
            JavaCompilerSettings.java21()
        ));

        assertThat(singleFile(index).sourceReferences())
            .containsExactly(new SourceReference("lib.LibraryType", libraryFile, lib, main));
    }

    @Test
    void index_withDotInSourceRootDirectory_resolvesReferencedSourceFile() {
        Path appSourceRoot = tempDir.resolve("workspace.with.dot/app/src/main/java");
        Path libSourceRoot = tempDir.resolve("dependency.with.dot/lib/src/main/java");
        Path sourceFile = appSourceRoot.resolve("app/UsesLibrary.java");
        Path libraryFile = libSourceRoot.resolve("lib/LibraryType.java");
        writeSource(
            sourceFile,
            """
            package app;

            import lib.LibraryType;

            public class UsesLibrary {
                private LibraryType libraryType;
            }
            """
        );
        writeSource(
            libraryFile,
            """
            package lib;

            public class LibraryType {
            }
            """
        );
        ProjectCoordinates app = new ProjectCoordinates(":app");
        SourceSetCoordinates main = new SourceSetCoordinates("main");
        ProjectCoordinates lib = new ProjectCoordinates(":lib");

        ProjectIndex index = indexer.index(new ProjectIndexingRequest(
            app,
            main,
            List.of(
                new SourceRoot(appSourceRoot, app, main),
                new SourceRoot(libSourceRoot, lib, main)
            ),
            List.of(sourceFile),
            List.of(),
            JavaCompilerSettings.java21()
        ));

        assertThat(singleFile(index).sourceReferences())
            .containsExactly(new SourceReference("lib.LibraryType", libraryFile.toAbsolutePath().normalize(), lib, main));
    }

    @Test
    void index_withDotInPackageDirectory_resolvesSameSourceRootReference() {
        Path sourceRoot = tempDir.resolve("workspace.with.dot/app/src/main/java");
        Path sourceFile = sourceRoot.resolve("example.with.dot/UsesHelper.java");
        Path helperFile = sourceRoot.resolve("example.with.dot/Helper.java");
        writeSource(
            sourceFile,
            """
            package example.with.dot;

            public class UsesHelper {
                private Helper helper;
            }
            """
        );
        writeSource(
            helperFile,
            """
            package example.with.dot;

            public class Helper {
            }
            """
        );

        ProjectIndex index = indexer.index(request(sourceRoot, List.of(sourceFile), List.of()));

        assertThat(singleFile(index).sourceReferences())
            .containsExactly(sourceReference("example.with.dot.Helper", helperFile.toAbsolutePath().normalize()));
    }

    @Test
    void index_withSourceReferenceAlsoOnClasspath_recordsOnlySourceReference() {
        Path fixtureRoot = fixturePath("jdt-indexer-source-reference-also-on-classpath");
        Path mainSourceRoot = fixtureRoot.resolve("src/main/java");
        Path testSourceRoot = fixtureRoot.resolve("src/test/java");
        Path sourceFile = testSourceRoot.resolve("example/UsesSharedMainType.java");
        Path targetFile = mainSourceRoot.resolve("example/SharedMainType.java").toAbsolutePath().normalize();
        Path classesDir = compileSourceFile(mainSourceRoot.resolve("example/SharedMainType.java"));
        ProjectCoordinates project = new ProjectCoordinates(":fixture");
        SourceSetCoordinates test = new SourceSetCoordinates("test");

        ProjectIndex index = indexer.index(new ProjectIndexingRequest(
            project,
            test,
            List.of(
                new SourceRoot(mainSourceRoot, project, new SourceSetCoordinates("main")),
                new SourceRoot(testSourceRoot, project, test)
            ),
            List.of(sourceFile),
            List.of(ClasspathEntry.of(classesDir, "main")),
            JavaCompilerSettings.java21()
        ));

        FileReferenceSet references = singleFile(index);
        assertThat(references.sourceReferences())
            .containsExactly(new SourceReference(
                "example.SharedMainType",
                targetFile,
                project,
                new SourceSetCoordinates("main")
            ));
        assertThat(references.binaryReferences()).isEmpty();
    }

    @Test
    void index_withAgronaReference_resolvesClasspathJar() {
        Path sourceRoot = fixtureSourceRoot("jdt-indexer-agrona-reference");
        Path sourceFile = sourceRoot.resolve("example/UsesAgrona.java");
        Path agronaJar = classpathJarContaining(IntArrayList.class);
        ClasspathEntry agrona = ClasspathEntry.of(agronaJar, "org.agrona:agrona:2.4.1");

        ProjectIndex index = indexer.index(ReferenceIndexingFixtures.requestWithClasspathEntries(
            sourceRoot,
            List.of(sourceFile),
            List.of(agrona)
        ));

        assertThat(singleFile(index).binaryReferences())
            .containsExactly(new BinaryReference(
                "org.agrona.collections.IntArrayList",
                "org.agrona:agrona:2.4.1",
                "org.agrona.collections.IntArrayList"
            ));
    }

    @Test
    void index_withClassDirectoryReference_resolvesClasspathDirectory() {
        Path sourceRoot = fixtureSourceRoot("jdt-indexer-class-directory-reference");
        Path sourceFile = sourceRoot.resolve("example/UsesCompiledDependency.java");
        Path classesDir = compileDependencyClass();

        ProjectIndex index = indexer.index(request(sourceRoot, List.of(sourceFile), List.of(classesDir)));

        assertThat(singleFile(index).binaryReferences())
            .containsExactly(new BinaryReference(
                "external.dep.CompiledDependency",
                classesDir.getFileName().toString(),
                "external.dep.CompiledDependency"
            ));
    }

    @Test
    void index_withNonZipFileOnClasspath_ignoresEntryWhenUnreferenced() {
        Path sourceRoot = fixtureSourceRoot("jdt-indexer-no-references");
        Path sourceFile = sourceRoot.resolve("example/Plain.java");
        Path nativeLibrary = createNativeLibraryClasspathFile("libnative.so");

        ProjectIndex index = indexer.index(request(sourceRoot, List.of(sourceFile), List.of(nativeLibrary)));

        FileReferenceSet references = singleFile(index);
        assertThat(references.sourceReferences()).isEmpty();
        assertThat(references.binaryReferences()).isEmpty();
        assertThat(references.unresolvedReferences()).isEmpty();
    }

    @Test
    void index_withJniSourceAndNonZipFileOnClasspath_ignoresNativeLibraryEntry() {
        Path sourceRoot = fixtureSourceRoot("jdt-indexer-jni-native-library");
        Path sourceFile = sourceRoot.resolve("example/UsesNativeLibrary.java");
        Path nativeLibrary = createNativeLibraryClasspathFile("Libbedrockio-89.so");
        ByteArrayOutputStream standardError = new ByteArrayOutputStream();

        ProjectIndex index = captureStandardError(
            standardError,
            () -> indexer.index(request(sourceRoot, List.of(sourceFile), List.of(nativeLibrary)))
        );

        FileReferenceSet references = singleFile(index);
        assertThat(references.sourceReferences()).isEmpty();
        assertThat(references.binaryReferences()).isEmpty();
        assertThat(references.unresolvedReferences()).isEmpty();
        assertThat(standardError.toString(StandardCharsets.UTF_8))
            .doesNotContain("Failed to init Classpath")
            .doesNotContain("ZipException");
    }

    private static SourceReference sourceReference(String qualifiedName, Path sourceFile) {
        return new SourceReference(
            qualifiedName,
            sourceFile,
            new ProjectCoordinates(":fixture"),
            new SourceSetCoordinates("main")
        );
    }

    private Path compileDependencyClass() {
        Path sourceDir = tempDir.resolve("dependency-src");
        Path classesDir = tempDir.resolve("dependency-classes").toAbsolutePath().normalize();
        Path sourceFile = sourceDir.resolve("external/dep/CompiledDependency.java");
        try {
            java.nio.file.Files.createDirectories(sourceFile.getParent());
            java.nio.file.Files.createDirectories(classesDir);
            java.nio.file.Files.writeString(
                sourceFile,
                """
                package external.dep;

                public class CompiledDependency {
                    public int value() {
                        return 42;
                    }
                }
                """
            );
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("Tests must run on a JDK, not a JRE").isNotNull();
        int result = compiler.run(null, null, null, "-d", classesDir.toString(), sourceFile.toString());
        assertThat(result).isZero();
        return classesDir;
    }

    private Path compileSourceFile(Path sourceFile) {
        Path classesDir = tempDir.resolve(sourceFile.getFileName().toString() + "-classes").toAbsolutePath().normalize();
        try {
            java.nio.file.Files.createDirectories(classesDir);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("Tests must run on a JDK, not a JRE").isNotNull();
        int result = compiler.run(null, null, null, "-d", classesDir.toString(), sourceFile.toString());
        assertThat(result).isZero();
        return classesDir;
    }

    private Path createNativeLibraryClasspathFile(String fileName) {
        Path file = tempDir.resolve(fileName).toAbsolutePath().normalize();
        try {
            java.nio.file.Files.write(file, new byte[] {0x7f, 'E', 'L', 'F', 1, 2, 3, 4});
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return file;
    }

    private static void writeSource(Path sourceFile, String content) {
        try {
            java.nio.file.Files.createDirectories(sourceFile.getParent());
            java.nio.file.Files.writeString(sourceFile, content);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ProjectIndex captureStandardError(ByteArrayOutputStream standardError, java.util.function.Supplier<ProjectIndex> action) {
        PrintStream originalStandardError = System.err;
        try (PrintStream capture = new PrintStream(standardError, true, StandardCharsets.UTF_8)) {
            System.setErr(capture);
            return action.get();
        } finally {
            System.setErr(originalStandardError);
        }
    }
}
