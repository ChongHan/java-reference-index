package io.github.chonghan.javareferenceindex.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublishedPluginTest {
    @TempDir
    Path tempDir;

    @Test
    void publishedPlugin_canIndexCleanGradleProject() throws IOException {
        Path project = copyFixtureProject("published-plugin/simple-java-project");

        BuildResult result;
        try {
            result = GradleRunner.create()
                .withProjectDir(project.toFile())
                .withArguments("--stacktrace", "indexJavaReferences")
                .build();
        } catch (UnexpectedBuildFailure failure) {
            Assumptions.assumeFalse(
                failure.getMessage().contains("Plugin [id: 'io.github.chonghan.java-reference-index', version: '0.1.0'] was not found"),
                "Published plugin io.github.chonghan.java-reference-index:0.1.0 is not visible from the Gradle Plugin Portal yet"
            );
            throw failure;
        }

        assertThat(result.task(":indexJavaReferences").getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);

        Path csv = project.resolve("build/reference-index/main-references.csv");
        assertThat(csv).isRegularFile();
        assertThat(Files.readAllLines(csv))
            .contains("source_project,source_path,target_kind,target_project,target")
            .contains(":,src/main/java/app/App.java,source,:,src/main/java/lib/LibraryType.java");
    }

    private Path copyFixtureProject(String fixtureName) throws IOException {
        Path fixturesRoot = Path.of(System.getProperty("javaReferenceIndex.integrationTestFixtures"))
            .toAbsolutePath()
            .normalize();
        Path fixture = fixturesRoot.resolve(fixtureName);
        Path project = tempDir.resolve("project");

        try (var paths = Files.walk(fixture)) {
            for (Path source : paths.sorted(Comparator.naturalOrder()).toList()) {
                Path target = project.resolve(fixture.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target);
                }
            }
        }

        return project;
    }
}
