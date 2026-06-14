package io.github.chonghan.javareferenceindex.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

class JavaReferenceQueryTaskTest extends GradlePluginTestKit {
    @Test
    void javaReferenceQuery_withSql_printsRowsFromGeneratedCsvFiles() throws IOException {
        copyFixture("single-project");

        var result = gradle(
            "javaReferenceQuery",
            "-Psql=select source_path, target_origin, target_path from java_references order by source_path, target_path"
        );

        assertThat(result.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":javaReferenceQuery").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("source_path,target_origin,target_path")
            .contains("src/main/java/example/App.java,source,src/main/java/example/Helper.java");
    }

    @Test
    void javaReferenceQuery_withQuietLogging_printsOnlyQueryResult() throws IOException {
        copyFixture("single-project");

        var result = gradle(
            "-q",
            "javaReferenceQuery",
            "-Psql=select source_path, target_origin, target_path from java_references order by source_path, target_path"
        );

        assertThat(result.task(":javaReferenceQuery").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput()).isEqualTo("""
            source_path,target_origin,target_path
            src/main/java/example/App.java,source,src/main/java/example/Helper.java
            """);
    }

    @Test
    void javaReferenceQuery_withSqlGradleProperty_printsRowsFromGeneratedCsvFiles() throws IOException {
        copyFixture("single-project");

        var result = gradle(
            "-q",
            "javaReferenceQuery",
            "-Psql=select source_path, target_origin, target_path from java_references order by source_path, target_path"
        );

        assertThat(result.task(":javaReferenceQuery").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput()).isEqualTo("""
            source_path,target_origin,target_path
            src/main/java/example/App.java,source,src/main/java/example/Helper.java
            """);
    }

    @Test
    void javaReferenceQuery_withSqlGradleProperty_reusesConfigurationCacheWhenSqlChanges() throws IOException {
        copyFixture("single-project");

        var firstResult = gradle(
            "javaReferenceQuery",
            "-Psql=select 20 as value",
            "--configuration-cache"
        );
        var secondResult = gradle(
            "javaReferenceQuery",
            "-Psql=select 21 as value",
            "--configuration-cache"
        );

        assertThat(firstResult.task(":javaReferenceQuery").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(firstResult.getOutput())
            .contains("value\n20")
            .contains("Configuration cache entry stored.");
        assertThat(secondResult.task(":javaReferenceQuery").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(secondResult.getOutput())
            .contains("value\n21")
            .contains("Configuration cache entry reused.");
    }

    @Test
    void javaReferenceQuery_withSqlGradleProperty_reusesConfigurationCacheWhenSourceFileChanges() throws IOException {
        copyFixture("single-project");

        var firstResult = gradle(
            "javaReferenceQuery",
            "-Psql=select count(*) as rows from java_references",
            "--configuration-cache"
        );
        Files.writeString(
            projectDir.resolve("src/main/java/example/App.java"),
            "\n// tiny source change\n",
            StandardOpenOption.APPEND
        );
        var secondResult = gradle(
            "javaReferenceQuery",
            "-Psql=select count(*) as rows from java_references",
            "--configuration-cache"
        );

        assertThat(firstResult.task(":javaReferenceQuery").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(firstResult.getOutput())
            .contains("rows\n1")
            .contains("Configuration cache entry stored.");
        assertThat(secondResult.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(secondResult.task(":javaReferenceQuery").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(secondResult.getOutput())
            .contains("rows\n1")
            .contains("Configuration cache entry reused.");
    }

    @Test
    void javaReferenceQuery_afterSourceFileChange_reindexesAndQueriesUpdatedCsv() throws IOException {
        copyFixture("single-project");
        String query = "select source_path, target_path from java_references order by source_path, target_path";

        var initialResult = gradle(
            "javaReferenceQuery",
            "-Psql=" + query,
            "--configuration-cache"
        );
        var upToDateResult = gradle(
            "javaReferenceQuery",
            "-Psql=" + query,
            "--configuration-cache"
        );
        Files.writeString(
            projectDir.resolve("src/main/java/example/App.java"),
            """
            package example;

            public class App {
            }
            """
        );
        var changedResult = gradle(
            "javaReferenceQuery",
            "-Psql=" + query,
            "--configuration-cache"
        );

        assertThat(initialResult.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(initialResult.getOutput())
            .contains("source_path,target_path")
            .contains("src/main/java/example/App.java,src/main/java/example/Helper.java");
        assertThat(upToDateResult.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);
        assertThat(upToDateResult.getOutput())
            .contains("source_path,target_path")
            .contains("src/main/java/example/App.java,src/main/java/example/Helper.java");
        assertThat(changedResult.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(changedResult.getOutput())
            .contains("source_path,target_path")
            .doesNotContain("src/main/java/example/App.java,src/main/java/example/Helper.java");
        assertThat(referencesCsv(projectDir))
            .containsExactly("source_project,source_path,target_origin,target_project,target_path,target_type");
    }

    @Test
    void javaReferenceQuery_withSqlTaskOption_isRejected() throws IOException {
        copyFixture("single-project");

        var result = gradleAndFail(
            "javaReferenceQuery",
            "--sql",
            "select 1"
        );

        assertThat(result.getOutput())
            .contains("Unknown command-line option '--sql'");
    }

    @Test
    void javaReferenceQuery_withMalformedSql_reportsDuckDbError() throws IOException {
        copyFixture("single-project");

        var result = gradleAndFail(
            "javaReferenceQuery",
            "-Psql=select from java_references"
        );

        assertThat(result.task(":javaReferenceQuery").getOutcome()).isEqualTo(TaskOutcome.FAILED);
        assertThat(result.getOutput())
            .contains("Parser Error")
            .doesNotContain("> Failed to query Java reference index CSV files");
    }

    @Test
    void javaReferenceQuery_fromRootProject_queriesSubprojectCsvFiles() throws IOException {
        copyFixture("multi-project");

        var result = gradle(
            ":javaReferenceQuery",
            "-Psql=select source_project, target_project, target_path from java_references where target_origin = 'source' order by target_path"
        );

        assertThat(result.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":app:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":javaReferenceQuery").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("source_project,target_project,target_path")
            .contains(":app,:lib,lib/src/main/java/lib/LibraryType.java");
    }

    @Test
    void javaReferenceQuery_withSubprojectWithoutJavaSources_queriesAvailableCsvFiles() throws IOException {
        copyFixture("subproject-without-java-sources");

        var result = gradle(
            ":javaReferenceQuery",
            "-Psql=select source_project, source_path, target_origin, target_path from java_references order by source_path, target_path"
        );

        assertThat(result.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":app:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":empty:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":javaReferenceQuery").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("source_project,source_path,target_origin,target_path")
            .contains(":app,app/src/main/java/app/App.java,source,app/src/main/java/app/Helper.java");
        assertThat(projectDir.resolve("empty/build/reference-index/main-references.csv")).doesNotExist();
    }

    @Test
    void javaReferenceQuery_fromSubprojectWithoutJavaSources_queriesEmptyTable() throws IOException {
        copyFixture("subproject-without-java-sources");

        var result = gradle(
            ":empty:javaReferenceQuery",
            "-Psql=select count(*) as rows from java_references"
        );

        assertThat(result.task(":empty:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":empty:javaReferenceQuery").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("rows\n0");
        assertThat(projectDir.resolve("empty/build/reference-index/main-references.csv")).doesNotExist();
    }
}
