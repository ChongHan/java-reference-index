package io.github.chonghan.javareferenceindex.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

class JavaReferenceQueryTaskTest extends GradlePluginTestKit {
    @Test
    void javaReferenceQuery_withSql_printsRowsFromGeneratedCsvFiles() throws IOException {
        copyFixture("single-project");

        var result = gradle(
            "javaReferenceQuery",
            "--sql",
            "select source_path, target_kind, target_path from java_references order by source_path, target_path"
        );

        assertThat(result.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":javaReferenceQuery").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("source_path,target_kind,target_path")
            .contains("src/main/java/example/App.java,source,src/main/java/example/Helper.java");
    }

    @Test
    void javaReferenceQuery_withQuietLogging_printsOnlyQueryResult() throws IOException {
        copyFixture("single-project");

        var result = gradle(
            "-q",
            "javaReferenceQuery",
            "--sql",
            "select source_path, target_kind, target_path from java_references order by source_path, target_path"
        );

        assertThat(result.task(":javaReferenceQuery").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput()).isEqualTo("""
            source_path,target_kind,target_path
            src/main/java/example/App.java,source,src/main/java/example/Helper.java
            """);
    }

    @Test
    void javaReferenceQuery_withSqlGradleProperty_printsRowsFromGeneratedCsvFiles() throws IOException {
        copyFixture("single-project");

        var result = gradle(
            "-q",
            "javaReferenceQuery",
            "-Psql=select source_path, target_kind, target_path from java_references order by source_path, target_path"
        );

        assertThat(result.task(":javaReferenceQuery").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput()).isEqualTo("""
            source_path,target_kind,target_path
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
    void javaReferenceQuery_fromRootProject_queriesSubprojectCsvFiles() throws IOException {
        copyFixture("multi-project");

        var result = gradle(
            ":javaReferenceQuery",
            "--sql",
            "select source_project, target_project, target_path from java_references where target_kind = 'source' order by target_path"
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
            "--sql",
            "select source_project, source_path, target_kind, target_path from java_references order by source_path, target_path"
        );

        assertThat(result.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":app:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":empty:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":javaReferenceQuery").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("source_project,source_path,target_kind,target_path")
            .contains(":app,app/src/main/java/app/App.java,source,app/src/main/java/app/Helper.java");
        assertThat(projectDir.resolve("empty/build/reference-index/main-references.csv")).doesNotExist();
    }

    @Test
    void javaReferenceQuery_fromSubprojectWithoutJavaSources_queriesEmptyTable() throws IOException {
        copyFixture("subproject-without-java-sources");

        var result = gradle(
            ":empty:javaReferenceQuery",
            "--sql",
            "select count(*) as rows from java_references"
        );

        assertThat(result.task(":empty:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":empty:javaReferenceQuery").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("rows\n0");
        assertThat(projectDir.resolve("empty/build/reference-index/main-references.csv")).doesNotExist();
    }
}
