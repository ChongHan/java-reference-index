package io.github.chonghan.javareferenceindex.gradle;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.io.TempDir;

abstract class GradlePluginTestKit {
    @TempDir
    Path projectDir;

    BuildResult gradle(String... arguments) {
        return gradle(projectDir, arguments);
    }

    BuildResult gradle(Path projectDirectory, String... arguments) {
        return gradleRunner(projectDirectory, arguments).build();
    }

    BuildResult gradleAndFail(String... arguments) {
        return gradleAndFail(projectDir, arguments);
    }

    BuildResult gradleAndFail(Path projectDirectory, String... arguments) {
        return gradleRunner(projectDirectory, arguments).buildAndFail();
    }

    private static GradleRunner gradleRunner(Path projectDirectory, String... arguments) {
        List<String> isolatedProjectsArguments = new ArrayList<>(Arrays.asList(arguments));
        isolatedProjectsArguments.add("-Dorg.gradle.unsafe.isolated-projects=true");

        return GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withPluginClasspath()
            .withArguments(isolatedProjectsArguments)
            .forwardOutput();
    }

    static List<String> referencesCsv(Path projectDirectory) throws IOException {
        return referencesCsv(projectDirectory, "main");
    }

    static List<String> referencesCsv(Path projectDirectory, String sourceSetName) throws IOException {
        return Files.readAllLines(projectDirectory.resolve("build/reference-index").resolve(sourceSetName + "-references.csv"));
    }

    static void enableLocalBuildCache(Path projectDirectory, Path buildCacheDirectory) throws IOException {
        String directory = buildCacheDirectory.toAbsolutePath().normalize().toString()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"");
        Files.writeString(
            projectDirectory.resolve("settings.gradle.kts"),
            """

            buildCache {
                local {
                    this.directory = file("%s")
                }
            }
            """.formatted(directory),
            StandardOpenOption.APPEND
        );
    }

    void copyFixture(String fixtureName) throws IOException {
        copyFixture(fixtureName, projectDir);
    }

    static void copyFixture(String fixtureName, Path targetRoot) throws IOException {
        Path fixtureRoot = fixtureRoot(fixtureName);
        try (Stream<Path> paths = Files.walk(fixtureRoot)) {
            for (Path source : paths.toList()) {
                Path target = targetRoot.resolve(fixtureRoot.relativize(source).toString());
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
            return Path.of(GradlePluginTestKit.class.getResource("/fixtures/" + fixtureName).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
