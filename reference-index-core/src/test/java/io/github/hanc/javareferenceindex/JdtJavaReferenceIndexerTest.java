package io.github.hanc.javareferenceindex;

import static org.assertj.core.api.Assertions.assertThat;
import static io.github.hanc.javareferenceindex.ReferenceIndexingFixtures.classpathJarContaining;
import static io.github.hanc.javareferenceindex.ReferenceIndexingFixtures.fixtureSourceRoot;
import static io.github.hanc.javareferenceindex.ReferenceIndexingFixtures.request;
import static io.github.hanc.javareferenceindex.ReferenceIndexingFixtures.singleFile;

import io.github.hanc.javareferenceindex.api.JavaReferenceIndexer;
import io.github.hanc.javareferenceindex.api.JavaReferenceIndexers;
import io.github.hanc.javareferenceindex.model.BinaryReference;
import io.github.hanc.javareferenceindex.model.FileReferenceSet;
import io.github.hanc.javareferenceindex.model.ProjectIndex;
import io.github.hanc.javareferenceindex.model.SourceReference;
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
            .containsExactly(new SourceReference("example.Helper", helperFile));
    }

    @Test
    void index_withAgronaReference_resolvesClasspathJar() {
        Path sourceRoot = fixtureSourceRoot("jdt-indexer-agrona-reference");
        Path sourceFile = sourceRoot.resolve("example/UsesAgrona.java");
        Path agronaJar = classpathJarContaining(IntArrayList.class);

        ProjectIndex index = indexer.index(request(sourceRoot, List.of(sourceFile), List.of(agronaJar)));

        assertThat(singleFile(index).binaryReferences())
            .containsExactly(new BinaryReference("org.agrona.collections.IntArrayList", agronaJar));
    }

    @Test
    void index_withClassDirectoryReference_resolvesClasspathDirectory() {
        Path sourceRoot = fixtureSourceRoot("jdt-indexer-class-directory-reference");
        Path sourceFile = sourceRoot.resolve("example/UsesCompiledDependency.java");
        Path classesDir = compileDependencyClass();

        ProjectIndex index = indexer.index(request(sourceRoot, List.of(sourceFile), List.of(classesDir)));

        assertThat(singleFile(index).binaryReferences())
            .containsExactly(new BinaryReference("external.dep.CompiledDependency", classesDir));
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
}
