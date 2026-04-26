package io.github.hanc.javareferenceindex.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaReferenceIndexPluginTest {
    @TempDir
    Path projectDir;

    @Test
    void indexJavaReferences_withSingleProject_writesSourceReferences() throws IOException {
        copyFixture("single-project");

        var result = gradle("indexJavaReferences");

        assertThat(result.task(":indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(referencesCsv(projectDir))
            .containsExactly(
                "source_project,source_path,target_kind,target_project,target",
                ":,src/main/java/example/App.java,source,:,src/main/java/example/Helper.java"
            );
    }

    @Test
    void indexJavaReferences_withProjectDependency_writesDependentProjectSourceReference() throws IOException {
        copyFixture("multi-project");

        var result = gradle(":app:indexJavaReferences");

        assertThat(result.task(":app:indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(referencesCsv(projectDir.resolve("app")))
            .containsExactly(
                "source_project,source_path,target_kind,target_project,target",
                ":app,app/src/main/java/app/App.java,source,:lib,lib/src/main/java/lib/LibraryType.java"
            );
    }

    @Test
    void indexJavaReferences_fromRootProject_indexesAllSubprojects() throws IOException {
        copyFixture("multi-project");

        var result = gradle(":indexJavaReferences");

        assertThat(result.task(":indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":app:indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":lib:indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":unused:indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(referencesCsv(projectDir.resolve("app")))
            .containsExactly(
                "source_project,source_path,target_kind,target_project,target",
                ":app,app/src/main/java/app/App.java,source,:lib,lib/src/main/java/lib/LibraryType.java"
            );
        assertThat(projectDir.resolve("lib/build/reference-index/main-references.csv")).isRegularFile();
        assertThat(projectDir.resolve("aaa-unused/build/reference-index/main-references.csv")).isRegularFile();
    }

    @Test
    void indexJavaReferences_withExternalDependency_writesBinaryReference() throws IOException {
        copyFixture("external-dependency");

        var result = gradle("indexJavaReferences");

        assertThat(result.task(":indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(referencesCsv(projectDir))
            .containsExactly(
                "source_project,source_path,target_kind,target_project,target",
                ":,src/main/java/example/App.java,binary,org.agrona:agrona:2.4.1,org.agrona.collections.IntArrayList"
            );
    }

    @Test
    void indexJavaReferences_withTestSourceSet_resolvesMainSourceReference() throws IOException {
        copyFixture("test-source-set");

        var result = gradle("indexJavaReferences");

        assertThat(result.task(":indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(referencesCsv(projectDir, "test"))
            .containsExactly(
                "source_project,source_path,target_kind,target_project,target",
                ":,src/test/java/example/MainTypeUsage.java,source,:,src/main/java/example/MainType.java"
            );
    }

    @Test
    void indexJavaReferences_withGeneratedSourceOnTestClasspath_resolvesGeneratedSourceReference() throws IOException {
        copyFixture("generated-source-set");

        var result = gradle("indexJavaReferences");

        assertThat(result.task(":compileGeneratedJava").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(referencesCsv(projectDir, "test"))
            .containsExactly(
                "source_project,source_path,target_kind,target_project,target",
                ":,src/test/java/example/GeneratedTypeUsage.java,source,:,build/generated-src/example/generated/GeneratedType.java"
            );
    }

    @Test
    void queryJavaReferences_withSql_printsRowsFromGeneratedCsvFiles() throws IOException {
        copyFixture("single-project");

        var result = gradle(
            "queryJavaReferences",
            "--sql",
            "select source_path, target_kind, target from java_references order by source_path, target"
        );

        assertThat(result.task(":indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":queryJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("source_path,target_kind,target")
            .contains("src/main/java/example/App.java,source,src/main/java/example/Helper.java");
    }

    @Test
    void queryJavaReferences_fromRootProject_queriesSubprojectCsvFiles() throws IOException {
        copyFixture("multi-project");

        var result = gradle(
            ":queryJavaReferences",
            "--sql",
            "select source_project, target_project, target from java_references where target_kind = 'source' order by target"
        );

        assertThat(result.task(":indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":app:indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":queryJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("source_project,target_project,target")
            .contains(":app,:lib,lib/src/main/java/lib/LibraryType.java");
    }

    private org.gradle.testkit.runner.BuildResult gradle(String... arguments) {
        return GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(arguments)
            .forwardOutput()
            .build();
    }

    private static List<String> referencesCsv(Path projectDirectory) throws IOException {
        return referencesCsv(projectDirectory, "main");
    }

    private static List<String> referencesCsv(Path projectDirectory, String sourceSetName) throws IOException {
        return Files.readAllLines(projectDirectory.resolve("build/reference-index").resolve(sourceSetName + "-references.csv"));
    }

    private void copyFixture(String fixtureName) throws IOException {
        Path fixtureRoot = fixtureRoot(fixtureName);
        try (Stream<Path> paths = Files.walk(fixtureRoot)) {
            for (Path source : paths.toList()) {
                Path target = projectDir.resolve(fixtureRoot.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target);
                }
            }
        }
    }

    private static Path fixtureRoot(String fixtureName) {
        try {
            return Path.of(JavaReferenceIndexPluginTest.class.getResource("/fixtures/" + fixtureName).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
