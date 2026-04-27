package io.github.chonghan.javareferenceindex.gradle;

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
    void indexJavaReferences_isRelocatableWithBuildCache() throws IOException {
        Path firstProject = projectDir.resolve("first");
        Path secondProject = projectDir.resolve("second");
        Path gradleUserHome = projectDir.resolve("gradle-user-home");
        copyFixture("single-project", firstProject);
        copyFixture("single-project", secondProject);

        var firstResult = gradle(
            firstProject,
            "--gradle-user-home",
            gradleUserHome.toString(),
            "--build-cache",
            "indexJavaReferences"
        );
        var secondResult = gradle(
            secondProject,
            "--gradle-user-home",
            gradleUserHome.toString(),
            "--build-cache",
            "indexJavaReferences"
        );

        assertThat(firstResult.task(":indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(secondResult.task(":indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.FROM_CACHE);
        assertThat(referencesCsv(secondProject))
            .containsExactly(
                "source_project,source_path,target_kind,target_project,target",
                ":,src/main/java/example/App.java,source,:,src/main/java/example/Helper.java"
            );
    }

    @Test
    void indexJavaReferences_fromCacheWithNoSources_doesNotDeleteExistingReferenceCsvFiles() throws IOException {
        Path firstProject = projectDir.resolve("first");
        Path secondProject = projectDir.resolve("second");
        Path gradleUserHome = projectDir.resolve("gradle-user-home");
        copyFixture("subproject-without-java-sources", firstProject);
        copyFixture("subproject-without-java-sources", secondProject);

        var firstResult = gradle(
            firstProject,
            "--gradle-user-home",
            gradleUserHome.toString(),
            "--build-cache",
            ":empty:indexJavaReferences"
        );
        Path existingCsv = secondProject.resolve("empty/build/reference-index/main-references.csv");
        Files.createDirectories(existingCsv.getParent());
        Files.writeString(
            existingCsv,
            """
            source_project,source_path,target_kind,target_project,target
            :empty,empty/src/main/java/example/App.java,source,:empty,empty/src/main/java/example/Helper.java
            """
        );

        var secondResult = gradle(
            secondProject,
            "--gradle-user-home",
            gradleUserHome.toString(),
            "--build-cache",
            ":empty:indexJavaReferences"
        );

        assertThat(firstResult.task(":empty:indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(secondResult.task(":empty:indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.FROM_CACHE);
        assertThat(existingCsv).isRegularFile();
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
    void queryJavaReferences_withQuietLogging_printsOnlyQueryResult() throws IOException {
        copyFixture("single-project");

        var result = gradle(
            "-q",
            "queryJavaReferences",
            "--sql",
            "select source_path, target_kind, target from java_references order by source_path, target"
        );

        assertThat(result.task(":queryJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput()).isEqualTo("""
            source_path,target_kind,target
            src/main/java/example/App.java,source,src/main/java/example/Helper.java
            """);
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

    @Test
    void queryJavaReferences_withSubprojectWithoutJavaSources_queriesAvailableCsvFiles() throws IOException {
        copyFixture("subproject-without-java-sources");

        var result = gradle(
            ":queryJavaReferences",
            "--sql",
            "select source_project, source_path, target_kind, target from java_references order by source_path, target"
        );

        assertThat(result.task(":indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":app:indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":empty:indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":queryJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("source_project,source_path,target_kind,target")
            .contains(":app,app/src/main/java/app/App.java,source,app/src/main/java/app/Helper.java");
        assertThat(projectDir.resolve("empty/build/reference-index/main-references.csv")).doesNotExist();
    }

    @Test
    void queryJavaReferences_fromSubprojectWithoutJavaSources_queriesEmptyTable() throws IOException {
        copyFixture("subproject-without-java-sources");

        var result = gradle(
            ":empty:queryJavaReferences",
            "--sql",
            "select count(*) as rows from java_references"
        );

        assertThat(result.task(":empty:indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":empty:queryJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("rows\n0");
        assertThat(projectDir.resolve("empty/build/reference-index/main-references.csv")).doesNotExist();
    }

    @Test
    void queryJavaReferences_help_describesUsageAndSchema() throws IOException {
        copyFixture("single-project");

        var result = gradle("help", "--task", "queryJavaReferences");

        assertThat(result.task(":help").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("Query Java reference edges with DuckDB SQL.")
            .contains("Table: java_references")
            .contains("Schema: source_project, source_path, target_kind, target_project, target")
            .contains("Columns: source_project/source_path identify the referencing file; target_kind is source, binary, or empty;")
            .contains("target_project is the target Gradle project path or library coordinate;")
            .contains("target is the referenced source path or binary type")
            .contains("Source row: :app,app/src/main/java/app/App.java,source,:lib,lib/src/main/java/lib/LibraryType.java")
            .contains("Binary row: :app,app/src/main/java/app/App.java,binary,org.agrona:agrona:2.4.1,org.agrona.DirectBuffer")
            .contains("Use -q for clean query output without Gradle task noise.")
            .contains("Repo-wide query from root: ./gradlew -q :queryJavaReferences --sql \"select * from java_references limit 20\"")
            .contains("Use the leading ':' from root; otherwise Gradle can run every queryJavaReferences task in root and subprojects.")
            .contains("What this file references: ./gradlew -q :queryJavaReferences --sql \"select target_project, target from java_references where source_path = 'app/src/main/java/app/App.java'\"")
            .contains("Who references this file: ./gradlew -q :queryJavaReferences --sql \"select source_project, source_path from java_references where target = 'lib/src/main/java/lib/LibraryType.java'\"")
            .contains("Options")
            .contains("--sql")
            .contains("SQL query to run against the java_references table.");
    }

    @Test
    void indexJavaReferences_help_describesOutputCsv() throws IOException {
        copyFixture("single-project");

        var result = gradle("help", "--task", "indexJavaReferences");

        assertThat(result.task(":help").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("Build Java reference edge CSVs.");
    }

    private org.gradle.testkit.runner.BuildResult gradle(String... arguments) {
        return gradle(projectDir, arguments);
    }

    private org.gradle.testkit.runner.BuildResult gradle(Path projectDirectory, String... arguments) {
        return GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
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
        copyFixture(fixtureName, projectDir);
    }

    private static void copyFixture(String fixtureName, Path targetRoot) throws IOException {
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
            return Path.of(JavaReferenceIndexPluginTest.class.getResource("/fixtures/" + fixtureName).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
