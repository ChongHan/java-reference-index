package io.github.chonghan.javareferenceindex.model;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class ModelValidationTest {
    private static final ProjectCoordinates PROJECT = new ProjectCoordinates(":app");
    private static final SourceSetCoordinates SOURCE_SET = new SourceSetCoordinates("main");
    private static final Path FILE = Path.of("Example.java");

    @TestFactory
    Stream<DynamicTest> requiredComponentsRejectNull() {
        JavaCompilerSettings settings = JavaCompilerSettings.java21();
        SourceReference sourceReference = new SourceReference("example.Type", FILE, PROJECT, SOURCE_SET);
        BinaryReference binaryReference = new BinaryReference("example.Type", "example:library:1", "example.Type");
        UnresolvedReference unresolvedReference = new UnresolvedReference("MissingType");

        return Stream.of(
            required("project path", "path", () -> new ProjectCoordinates(null)),
            required("source-set name", "name", () -> new SourceSetCoordinates(null)),
            required("source-root path", "path", () -> new SourceRoot(null, PROJECT, SOURCE_SET)),
            required("source-root project", "project", () -> new SourceRoot(FILE, null, SOURCE_SET)),
            required("source-root source set", "sourceSet", () -> new SourceRoot(FILE, PROJECT, null)),
            required("compiler source level", "sourceLevel", () -> new JavaCompilerSettings(null, settings.targetLevel(), null, StandardCharsets.UTF_8)),
            required("compiler target level", "targetLevel", () -> new JavaCompilerSettings(settings.sourceLevel(), null, null, StandardCharsets.UTF_8)),
            required("compiler encoding", "encoding", () -> new JavaCompilerSettings(settings.sourceLevel(), settings.targetLevel(), null, null)),
            required("request project", "project", () -> request(null, SOURCE_SET, List.of(), List.of(), List.of(), settings)),
            required("request source set", "sourceSet", () -> request(PROJECT, null, List.of(), List.of(), List.of(), settings)),
            required("request source roots", "sourceRoots", () -> request(PROJECT, SOURCE_SET, null, List.of(), List.of(), settings)),
            required("request source files", "sourceFiles", () -> request(PROJECT, SOURCE_SET, List.of(), null, List.of(), settings)),
            required("request classpath", "classpathEntries", () -> request(PROJECT, SOURCE_SET, List.of(), List.of(), null, settings)),
            required("request compiler settings", "compilerSettings", () -> request(PROJECT, SOURCE_SET, List.of(), List.of(), List.of(), null)),
            required("source reference name", "qualifiedName", () -> new SourceReference(null, FILE, PROJECT, SOURCE_SET)),
            required("source reference file", "sourceFile", () -> new SourceReference("example.Type", null, PROJECT, SOURCE_SET)),
            required("source reference project", "targetProject", () -> new SourceReference("example.Type", FILE, null, SOURCE_SET)),
            required("source reference source set", "targetSourceSet", () -> new SourceReference("example.Type", FILE, PROJECT, null)),
            required("binary reference name", "qualifiedName", () -> new BinaryReference(null, "target", "example.Type")),
            required("binary reference target", "targetProject", () -> new BinaryReference("example.Type", null, "example.Type")),
            required("binary reference symbol", "referenceSymbol", () -> new BinaryReference("example.Type", "target", null)),
            required("unresolved name", "name", () -> new UnresolvedReference(null)),
            required("file-reference source file", "sourceFile", () -> new FileReferenceSet(null, List.of(), List.of(), List.of())),
            required("file-reference source references", "sourceReferences", () -> new FileReferenceSet(FILE, null, List.of(), List.of())),
            required("file-reference binary references", "binaryReferences", () -> new FileReferenceSet(FILE, List.of(), null, List.of())),
            required("file-reference unresolved references", "unresolvedReferences", () -> new FileReferenceSet(FILE, List.of(), List.of(), null)),
            required("index project", "project", () -> new ProjectIndex(null, SOURCE_SET, List.of())),
            required("index source set", "sourceSet", () -> new ProjectIndex(PROJECT, null, List.of())),
            required("index files", "files", () -> new ProjectIndex(PROJECT, SOURCE_SET, null)),
            required("source-reference list element", null, () -> new FileReferenceSet(FILE, List.of(sourceReference, null), List.of(), List.of())),
            required("binary-reference list element", null, () -> new FileReferenceSet(FILE, List.of(), List.of(binaryReference, null), List.of())),
            required("unresolved-reference list element", null, () -> new FileReferenceSet(FILE, List.of(), List.of(), List.of(unresolvedReference, null)))
        ).map(testCase -> dynamicTest(testCase.name(), () -> {
            var assertion = assertThatNullPointerException().isThrownBy(testCase.action());
            if (testCase.message() != null) {
                assertion.withMessage(testCase.message());
            }
        }));
    }

    private static ProjectIndexingRequest request(
        ProjectCoordinates project,
        SourceSetCoordinates sourceSet,
        List<SourceRoot> sourceRoots,
        List<Path> sourceFiles,
        List<ClasspathEntry> classpathEntries,
        JavaCompilerSettings settings
    ) {
        return new ProjectIndexingRequest(project, sourceSet, sourceRoots, sourceFiles, classpathEntries, settings);
    }

    private static NullCase required(String name, String message, ThrowingCallable action) {
        return new NullCase(name, message, action);
    }

    private record NullCase(String name, String message, ThrowingCallable action) {}
}
