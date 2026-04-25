package io.github.hanc.javareferenceindex.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaReferenceIndexPluginTest {
    @TempDir
    Path projectDir;

    @Test
    void indexJavaReferences_withSingleProject_printsSourceReferences() throws IOException {
        copyFixture("single-project");

        var result = gradle("indexJavaReferences");

        assertThat(result.task(":indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("project=:")
            .contains("sourceSet=main")
            .contains("file=example.App")
            .contains("sourceRef=example.Helper");
    }

    @Test
    void indexJavaReferences_withProjectDependency_printsDependentProjectSourceReference() throws IOException {
        copyFixture("multi-project");

        var result = gradle(":app:indexJavaReferences");

        assertThat(result.task(":app:indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("project=:app")
            .contains("file=app.App")
            .contains("sourceRef=lib.LibraryType")
            .contains("targetProject=:lib");
    }

    @Test
    void indexJavaReferences_withExternalDependency_printsBinaryReference() throws IOException {
        copyFixture("external-dependency");

        var result = gradle("indexJavaReferences");

        assertThat(result.task(":indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("file=example.App")
            .contains("binaryRef=org.agrona.collections.IntArrayList")
            .contains("agrona");
    }

    private org.gradle.testkit.runner.BuildResult gradle(String... arguments) {
        return GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(arguments)
            .forwardOutput()
            .build();
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
