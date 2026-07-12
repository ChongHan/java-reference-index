package io.github.chonghan.javareferenceindex.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

class RealWorldPluginTest {
    @TempDir
    Path tempDir;

    @Test
    void javaReferenceIndex_withAgronaSubmodule_writesQueryableCsvFiles() throws IOException {
        Path agrona = realWorldProject("agrona");
        var result = javaReferenceIndex(agrona, true);

        assertThat(result.task(":javaReferenceIndexAll").getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);
        assertThat(result.task(":javaReferenceIndex").getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);
        assertThat(result.task(":agrona:javaReferenceIndex").getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);
        assertThat(result.task(":agrona-agent:javaReferenceIndex").getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);

        assertReferenceCsvIsUseful(agrona, agrona.resolve("agrona/build/reference-index/main-references.csv"));

        Path agronaAgentCsv = agrona.resolve("agrona-agent/build/reference-index/main-references.csv");
        List<CsvRow> agronaAgentRows = assertReferenceCsvIsUseful(agrona, agronaAgentCsv);
        assertThat(agronaAgentRows)
            .anySatisfy(row -> {
                assertThat(row.sourceProject()).isEqualTo(":agrona-agent");
                assertThat(row.sourcePath()).startsWith("agrona-agent/src/main/java/");
                assertThat(row.targetOrigin()).isEqualTo("source");
                assertThat(row.targetProject()).isEqualTo(":agrona");
                assertThat(row.targetPath()).startsWith("agrona/src/main/java/");
            })
            .anySatisfy(row -> {
                assertThat(row.sourceProject()).isEqualTo(":agrona-agent");
                assertThat(row.targetOrigin()).isEqualTo("binary");
                assertThat(row.targetProject()).isEqualTo("net.bytebuddy:byte-buddy:1.18.8");
                assertThat(row.targetPath()).isEmpty();
                assertThat(row.referenceSymbol()).isEqualTo("net.bytebuddy.agent.builder.AgentBuilder");
            });
    }

    @Test
    void javaReferenceIndex_withDisruptorSubmodule_writesQueryableCsvFiles() throws IOException {
        Path disruptor = disruptorProject();
        var result = javaReferenceIndex(disruptor, true);

        assertThat(result.task(":javaReferenceIndexAll").getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);
        assertThat(result.task(":javaReferenceIndex").getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);

        Path mainCsv = disruptor.resolve("build/reference-index/main-references.csv");
        List<CsvRow> mainRows = assertReferenceCsvIsUseful(disruptor, mainCsv);
        assertThat(mainRows).anySatisfy(row -> {
            assertThat(row.sourceProject()).isEqualTo(":");
            assertThat(row.sourcePath()).isEqualTo("src/main/java/com/lmax/disruptor/RingBuffer.java");
            assertThat(row.targetOrigin()).isEqualTo("source");
            assertThat(row.targetProject()).isEqualTo(":");
            assertThat(row.targetPath()).isEqualTo("src/main/java/com/lmax/disruptor/Sequencer.java");
            assertThat(row.referenceSymbol()).isEqualTo("com.lmax.disruptor.Sequencer");
        });

        Path examplesCsv = disruptor.resolve("build/reference-index/examples-references.csv");
        List<CsvRow> examplesRows = assertReferenceCsvIsUseful(disruptor, examplesCsv);
        assertThat(examplesRows).anySatisfy(row -> {
            assertThat(row.sourceProject()).isEqualTo(":");
            assertThat(row.sourcePath()).startsWith("src/examples/java/");
            assertThat(row.targetOrigin()).isEqualTo("source");
            assertThat(row.targetProject()).isEqualTo(":");
            assertThat(row.targetPath()).startsWith("src/main/java/com/lmax/disruptor/");
            assertThat(row.referenceSymbol()).startsWith("com.lmax.disruptor.");
        });
    }

    @Test
    void javaReferenceIndex_withAeronSubmodule_writesQueryableCsvFiles() throws IOException {
        Path aeron = realWorldProject("aeron");
        var result = javaReferenceIndex(aeron, false);

        assertThat(result.task(":javaReferenceIndexAll").getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);
        assertThat(result.task(":javaReferenceIndex").getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);
        assertThat(result.task(":aeron-client:javaReferenceIndex").getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);
        assertThat(result.task(":aeron-driver:javaReferenceIndex").getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);
        assertThat(result.task(":aeron-archive:javaReferenceIndex").getOutcome()).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);

        Path aeronClientCsv = aeron.resolve("aeron-client/build/reference-index/main-references.csv");
        List<CsvRow> aeronClientRows = assertReferenceCsvIsUseful(aeron, aeronClientCsv);
        assertThat(aeronClientRows)
            .anySatisfy(row -> {
                assertThat(row.sourceProject()).isEqualTo(":aeron-client");
                assertThat(row.targetOrigin()).isEqualTo("binary");
                assertThat(row.targetProject()).startsWith("org.agrona:agrona:");
                assertThat(row.targetPath()).isEmpty();
                assertThat(row.referenceSymbol()).startsWith("org.agrona.");
            });

        Path aeronArchiveCsv = aeron.resolve("aeron-archive/build/reference-index/main-references.csv");
        List<CsvRow> aeronArchiveRows = assertReferenceCsvIsUseful(aeron, aeronArchiveCsv);
        assertThat(aeronArchiveRows)
            .anySatisfy(row -> {
                assertThat(row.sourceProject()).isEqualTo(":aeron-archive");
                assertThat(row.targetOrigin()).isEqualTo("source");
                assertThat(row.targetProject()).isEqualTo(":aeron-client");
                assertThat(row.targetPath()).startsWith("aeron-client/src/main/java/");
            });
    }

    private Path disruptorProject() throws IOException {
        Path upstream = realWorldProject("disruptor");
        Path project = tempDir.resolve("disruptor");
        // The pinned upstream build uses a jcstress plugin tied to Gradle's removed
        // JavaPluginConvention. Keep its sources and source-set layout, but run them
        // from a minimal Gradle 9-compatible build without modifying the submodule.
        copyDirectory(upstream.resolve("src"), project.resolve("src"));
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'disruptor'\n");
        Files.writeString(
            project.resolve("build.gradle"),
            """
            plugins {
                id 'java-library'
            }

            group = 'com.lmax'
            version = '4.0.0'
            java {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:5.9.0'
                testImplementation 'org.hamcrest:hamcrest:2.2'
            }

            sourceSets {
                examples {
                    compileClasspath += sourceSets.main.output
                    runtimeClasspath += sourceSets.main.output
                }
            }
            """
        );
        return project;
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination);
                }
            }
        }
    }

    private Path realWorldProject(String name) {
        Path fixturesRoot = Path.of(System.getProperty("javaReferenceIndex.integrationTestFixtures"))
            .toAbsolutePath()
            .normalize();
        Path project = fixturesRoot.resolve("real-world-projects").resolve(name);
        Assumptions.assumeTrue(
            Files.isRegularFile(project.resolve("settings.gradle")),
            name + " submodule is not initialized"
        );
        return project;
    }

    private org.gradle.testkit.runner.BuildResult javaReferenceIndex(Path project, boolean configurationCache) throws IOException {
        Path initScript = tempDir.resolve(project.getFileName() + "-apply-java-reference-index.gradle");
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
                    apply plugin: io.github.chonghan.javareferenceindex.gradle.JavaReferenceIndexPlugin
                }
            }
            """.formatted(pluginClasspathFiles())
        );

        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.put("BUILD_JAVA_HOME", System.getProperty("java.home"));
        deleteIfExists(project.resolve(".gradle/configuration-cache"));

        // Do not use --rerun-tasks here: changes to the plugin implementation already
        // invalidate its tasks, while that flag needlessly recompiles every real-world project.
        List<String> arguments = new ArrayList<>(List.of(
                "--init-script", initScript.toString(),
                "--configure-on-demand",
                "--parallel",
                "--quiet",
                "--stacktrace",
                ":javaReferenceIndexAll"
        ));
        if (configurationCache) {
            arguments.add(3, "--configuration-cache");
        }

        return GradleRunner.create()
            .withProjectDir(project.toFile())
            .withArguments(arguments)
            .withEnvironment(environment)
            .build();
    }

    private static List<CsvRow> assertReferenceCsvIsUseful(Path projectRoot, Path csvFile) throws IOException {
        assertThat(csvFile).isRegularFile();
        List<String> lines = Files.readAllLines(csvFile);
        assertThat(lines)
            .isNotEmpty()
            .first()
            .isEqualTo("source_project,source_path,target_origin,target_project,target_path,reference_symbol");
        assertThat(lines).hasSizeGreaterThan(1);

        List<CsvRow> rows = lines.stream()
            .skip(1)
            .map(RealWorldPluginTest::parseCsvRow)
            .toList();
        assertThat(rows).anyMatch(row -> "source".equals(row.targetOrigin()));

        rows.stream()
            .limit(100)
            .forEach(row -> {
                assertThat(projectRoot.resolve(row.sourcePath())).isRegularFile();
                if ("source".equals(row.targetOrigin())) {
                    assertThat(row.targetProject()).isNotBlank();
                    assertThat(projectRoot.resolve(row.targetPath())).isRegularFile();
                    assertThat(row.referenceSymbol()).isNotBlank();
                } else if ("binary".equals(row.targetOrigin())) {
                    assertThat(row.targetProject()).isNotBlank();
                    assertThat(row.targetPath()).isEmpty();
                    assertThat(row.referenceSymbol()).isNotBlank();
                }
            });
        return rows;
    }

    private static CsvRow parseCsvRow(String line) {
        String[] columns = line.split(",", -1);
        assertThat(columns).hasSize(6);
        return new CsvRow(columns[0], columns[1], columns[2], columns[3], columns[4], columns[5]);
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
        try (InputStream input = RealWorldPluginTest.class.getClassLoader()
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
        String targetOrigin,
        String targetProject,
        String targetPath,
        String referenceSymbol
    ) {}
}
