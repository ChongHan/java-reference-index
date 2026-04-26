package io.github.hanc.javareferenceindex.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgronaRealWorldPluginTest {
    @TempDir
    Path tempDir;

    @Test
    void indexJavaReferences_withAgronaSubmodule_writesQueryableCsvFiles() throws IOException {
        Path fixturesRoot = Path.of(System.getProperty("javaReferenceIndex.integrationTestFixtures"))
            .toAbsolutePath()
            .normalize();
        Path agrona = fixturesRoot.resolve("real-world-projects/agrona");
        Assumptions.assumeTrue(
            Files.isRegularFile(agrona.resolve("settings.gradle")),
            "Agrona submodule is not initialized"
        );

        Path initScript = tempDir.resolve("apply-java-reference-index.gradle");
        Files.writeString(
            initScript,
            """
            initscript {
                dependencies {
                    classpath files(%s)
                }
            }

            allprojects {
                plugins.withId("java") {
                    apply plugin: io.github.hanc.javareferenceindex.gradle.JavaReferenceIndexPlugin
                }
            }
            """.formatted(pluginClasspathFiles())
        );

        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.put("BUILD_JAVA_HOME", System.getProperty("java.home"));
        deleteIfExists(agrona.resolve(".gradle/configuration-cache"));

        var result = GradleRunner.create()
            .withProjectDir(agrona.toFile())
            .withArguments(
                "--init-script", initScript.toString(),
                "--no-parallel",
                "--configuration-cache",
                "--rerun-tasks",
                "--quiet",
                "--stacktrace",
                ":indexJavaReferences"
            )
            .withEnvironment(environment)
            .build();

        assertThat(result.task(":indexJavaReferences").getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);
        assertThat(result.task(":agrona:indexJavaReferences").getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);
        assertThat(result.task(":agrona-agent:indexJavaReferences").getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);

        assertReferenceCsvIsUseful(agrona, agrona.resolve("agrona/build/reference-index/main-references.csv"));

        Path agronaAgentCsv = agrona.resolve("agrona-agent/build/reference-index/main-references.csv");
        List<CsvRow> agronaAgentRows = assertReferenceCsvIsUseful(agrona, agronaAgentCsv);
        assertThat(agronaAgentRows)
            .anySatisfy(row -> {
                assertThat(row.sourceProject()).isEqualTo(":agrona-agent");
                assertThat(row.sourcePath()).startsWith("agrona-agent/src/main/java/");
                assertThat(row.targetKind()).isEqualTo("source");
                assertThat(row.targetProject()).isEqualTo(":agrona");
                assertThat(row.target()).startsWith("agrona/src/main/java/");
            })
            .anySatisfy(row -> {
                assertThat(row.sourceProject()).isEqualTo(":agrona-agent");
                assertThat(row.targetKind()).isEqualTo("binary");
                assertThat(row.targetProject()).isEqualTo("net.bytebuddy:byte-buddy:1.18.8");
                assertThat(row.target()).isEqualTo("net.bytebuddy.agent.builder.AgentBuilder");
            });
    }

    private static List<CsvRow> assertReferenceCsvIsUseful(Path agronaRoot, Path csvFile) throws IOException {
        assertThat(csvFile).isRegularFile();
        List<String> lines = Files.readAllLines(csvFile);
        assertThat(lines)
            .isNotEmpty()
            .first()
            .isEqualTo("source_project,source_path,target_kind,target_project,target");
        assertThat(lines).hasSizeGreaterThan(1);

        List<CsvRow> rows = lines.stream()
            .skip(1)
            .map(AgronaRealWorldPluginTest::parseCsvRow)
            .toList();
        assertThat(rows).anyMatch(row -> "source".equals(row.targetKind()));

        rows.stream()
            .limit(100)
            .forEach(row -> {
                assertThat(agronaRoot.resolve(row.sourcePath())).isRegularFile();
                if ("source".equals(row.targetKind())) {
                    assertThat(row.targetProject()).isNotBlank();
                    assertThat(agronaRoot.resolve(row.target())).isRegularFile();
                } else if ("binary".equals(row.targetKind())) {
                    assertThat(row.targetProject()).isNotBlank();
                    assertThat(row.target()).isNotBlank();
                }
            });
        return rows;
    }

    private static CsvRow parseCsvRow(String line) {
        String[] columns = line.split(",", -1);
        assertThat(columns).hasSize(5);
        return new CsvRow(columns[0], columns[1], columns[2], columns[3], columns[4]);
    }

    private static void deleteIfExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            for (Path candidate : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(candidate);
            }
        }
    }

    private static String pluginClasspathFiles() {
        return Arrays.stream(pluginClasspath().split(File.pathSeparator))
            .map(path -> "'" + path.replace("\\", "\\\\").replace("'", "\\'") + "'")
            .collect(Collectors.joining(", "));
    }

    private static String pluginClasspath() {
        Properties properties = new Properties();
        try (InputStream input = AgronaRealWorldPluginTest.class.getClassLoader()
            .getResourceAsStream("plugin-under-test-metadata.properties")) {
            if (input == null) {
                throw new IllegalStateException("Missing plugin-under-test-metadata.properties");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties.getProperty("implementation-classpath");
    }

    private record CsvRow(
        String sourceProject,
        String sourcePath,
        String targetKind,
        String targetProject,
        String target
    ) {}
}
