package io.github.chonghan.javareferenceindex.internal.resolve;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chonghan.javareferenceindex.model.BinaryReference;
import io.github.chonghan.javareferenceindex.model.ClasspathEntry;
import io.github.chonghan.javareferenceindex.model.JavaCompilerSettings;
import io.github.chonghan.javareferenceindex.model.ProjectCoordinates;
import io.github.chonghan.javareferenceindex.model.ProjectIndexingRequest;
import io.github.chonghan.javareferenceindex.model.SourceRoot;
import io.github.chonghan.javareferenceindex.model.SourceSetCoordinates;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceAndClasspathTypeReferenceResolverTest {
    private final SourceAndClasspathTypeReferenceResolver resolver = new SourceAndClasspathTypeReferenceResolver();

    @TempDir
    Path tempDir;

    @Test
    void resolveSource_withNestedType_returnsEnclosingSourceFile() throws IOException {
        ProjectCoordinates app = new ProjectCoordinates(":app");
        ProjectCoordinates lib = new ProjectCoordinates(":lib");
        SourceSetCoordinates main = new SourceSetCoordinates("main");
        Path appFile = tempDir.resolve("app/src/main/java/app/App.java");
        Path libRoot = tempDir.resolve("lib/src/main/java");
        Path outerFile = libRoot.resolve("example/Outer.java");
        createFile(appFile);
        createFile(outerFile);

        var reference = resolver.resolveSource(
            "example.Outer.Inner",
            appFile,
            request(app, main, List.of(new SourceRoot(libRoot, lib, main)), List.of(appFile))
        );

        assertThat(reference).hasValueSatisfying(sourceReference -> {
            assertThat(sourceReference.qualifiedName()).isEqualTo("example.Outer.Inner");
            assertThat(sourceReference.sourceFile()).isEqualTo(outerFile.toAbsolutePath().normalize());
            assertThat(sourceReference.targetProject()).isEqualTo(lib);
            assertThat(sourceReference.targetSourceSet()).isEqualTo(main);
        });
    }

    @Test
    void resolveSource_withDuplicateTopLevelTypes_keepsSourceRootOrder() throws IOException {
        ProjectCoordinates app = new ProjectCoordinates(":app");
        SourceSetCoordinates main = new SourceSetCoordinates("main");
        Path appFile = tempDir.resolve("app/src/main/java/app/App.java");
        Path firstRoot = tempDir.resolve("first/src/main/java");
        Path secondRoot = tempDir.resolve("second/src/main/java");
        Path firstDuplicate = firstRoot.resolve("example/Duplicate.java");
        Path secondDuplicate = secondRoot.resolve("example/Duplicate.java");
        createFile(appFile);
        createFile(firstDuplicate);
        createFile(secondDuplicate);

        var reference = resolver.resolveSource(
            "example.Duplicate",
            appFile,
            request(app, main, List.of(
                new SourceRoot(firstRoot, new ProjectCoordinates(":first"), main),
                new SourceRoot(secondRoot, new ProjectCoordinates(":second"), main)
            ), List.of(appFile))
        );

        assertThat(reference).hasValueSatisfying(sourceReference -> {
            assertThat(sourceReference.sourceFile()).isEqualTo(firstDuplicate.toAbsolutePath().normalize());
            assertThat(sourceReference.targetProject()).isEqualTo(new ProjectCoordinates(":first"));
        });
    }

    @Test
    void resolveSource_afterSourceRootChanges_refreshesLookup() throws IOException {
        ProjectCoordinates app = new ProjectCoordinates(":app");
        ProjectCoordinates lib = new ProjectCoordinates(":lib");
        SourceSetCoordinates main = new SourceSetCoordinates("main");
        Path appFile = tempDir.resolve("app/src/main/java/app/App.java");
        Path libRoot = tempDir.resolve("lib/src/main/java");
        Path addedFile = libRoot.resolve("example/Added.java");
        createFile(appFile);
        ProjectIndexingRequest request = request(
            app,
            main,
            List.of(new SourceRoot(libRoot, lib, main)),
            List.of(appFile)
        );

        assertThat(resolver.resolveSource("example.Added", appFile, request)).isEmpty();
        createFile(addedFile);

        assertThat(resolver.resolveSource("example.Added", appFile, request))
            .hasValueSatisfying(reference -> assertThat(reference.sourceFile())
                .isEqualTo(addedFile.toAbsolutePath().normalize()));
    }

    @Test
    void resolveSource_withSameSourceFile_ignoresSelfReference() throws IOException {
        ProjectCoordinates project = new ProjectCoordinates(":app");
        SourceSetCoordinates main = new SourceSetCoordinates("main");
        Path sourceRoot = tempDir.resolve("app/src/main/java");
        Path sourceFile = sourceRoot.resolve("example/Self.java");
        createFile(sourceFile);

        var reference = resolver.resolveSource(
            "example.Self",
            sourceFile,
            request(project, main, List.of(new SourceRoot(sourceRoot, project, main)), List.of(sourceFile))
        );

        assertThat(reference).isEmpty();
    }

    @Test
    void resolveBinary_afterClasspathDirectoryChanges_refreshesLookup() throws IOException {
        Path classes = tempDir.resolve("classes");
        Files.createDirectories(classes);
        ClasspathEntry entry = ClasspathEntry.of(classes, "example:changing:1.0");
        ProjectIndexingRequest request = requestWithClasspath(entry);

        assertThat(resolver.resolveBinary("example.Added", request)).isEmpty();
        createFile(classes.resolve("example/Added.class"));

        assertThat(resolver.resolveBinary("example.Added", request)).contains(new BinaryReference(
            "example.Added",
            "example:changing:1.0",
            "example.Added"
        ));
    }

    @Test
    void resolveBinary_withDollarInTopLevelClassName_preservesDollar() throws IOException {
        Path classes = tempDir.resolve("classes");
        createFile(classes.resolve("example/Foo$Bar.class"));
        ClasspathEntry entry = ClasspathEntry.of(classes, "example:dollar-name:1.0");
        ProjectIndexingRequest request = requestWithClasspath(entry);

        var reference = resolver.resolveBinary("example.Foo$Bar", request);

        assertThat(reference).contains(new BinaryReference(
            "example.Foo$Bar",
            "example:dollar-name:1.0",
            "example.Foo$Bar"
        ));
    }

    @Test
    void resolveBinary_withClassNewerThanConfiguredRelease_ignoresClass() throws IOException {
        Path jar = tempDir.resolve("future-multi-release.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Multi-Release", "true");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            output.putNextEntry(new JarEntry("META-INF/versions/22/example/FutureType.class"));
            output.write(new byte[] {0});
            output.closeEntry();
        }
        ClasspathEntry entry = ClasspathEntry.of(jar, "example:future:1.0");
        ProjectIndexingRequest request = requestWithClasspath(entry);

        assertThat(resolver.resolveBinary("example.FutureType", request)).isEmpty();
    }

    @Test
    void resolveBinary_withVersionOnlyMultiReleaseClass_usesLogicalClassName() throws IOException {
        Path jar = tempDir.resolve("multi-release.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Multi-Release", "true");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            output.putNextEntry(new JarEntry("META-INF/versions/9/example/VersionOnly.class"));
            output.write(new byte[] {0});
            output.closeEntry();
        }
        ClasspathEntry entry = ClasspathEntry.of(jar, "example:multi-release:1.0");
        ProjectIndexingRequest request = requestWithClasspath(entry);

        var reference = resolver.resolveBinary("example.VersionOnly", request);

        assertThat(reference).contains(new BinaryReference(
            "example.VersionOnly",
            "example:multi-release:1.0",
            "example.VersionOnly"
        ));
    }

    private static ProjectIndexingRequest requestWithClasspath(ClasspathEntry entry) {
        return new ProjectIndexingRequest(
            new ProjectCoordinates(":app"),
            new SourceSetCoordinates("main"),
            List.of(),
            List.of(),
            List.of(entry),
            JavaCompilerSettings.java21()
        );
    }

    private static ProjectIndexingRequest request(
        ProjectCoordinates project,
        SourceSetCoordinates sourceSet,
        List<SourceRoot> sourceRoots,
        List<Path> sourceFiles
    ) {
        return new ProjectIndexingRequest(
            project,
            sourceSet,
            sourceRoots,
            sourceFiles,
            List.of(),
            JavaCompilerSettings.java21()
        );
    }

    private static void createFile(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "");
    }
}
