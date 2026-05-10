package io.github.chonghan.javareferenceindex.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

class JavaReferenceHelpTaskTest extends GradlePluginTestKit {
    @Test
    void javaReferenceQuery_help_describesUsageAndSchema() throws IOException {
        copyFixture("single-project");

        var result = gradle("help", "--task", "javaReferenceQuery");

        assertThat(result.task(":help").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("Query Java reference edges with DuckDB SQL.")
            .contains("Table: java_references")
            .contains("Schema: source_project, source_path, target_kind, target_project, target_path, target_type")
            .contains("Columns: source_project/source_path identify the referencing file; target_kind is source, binary, or empty;")
            .contains("target_project is the target Gradle project path or library coordinate;")
            .contains("target_path is the referenced source path for source references and empty for binary references")
            .contains("target_type is the referenced Java type name")
            .contains("Source row: :app,app/src/main/java/app/App.java,source,:lib,lib/src/main/java/lib/LibraryType.java,lib.LibraryType")
            .contains("Binary row: :app,app/src/main/java/app/App.java,binary,org.agrona:agrona:2.4.1,,org.agrona.DirectBuffer")
            .contains("Use -q for clean query output without Gradle task noise.")
            .contains("Repo-wide query from root: ./gradlew -q :javaReferenceQuery --sql \"select * from java_references limit 20\"")
            .contains("Root query depends on :javaReferenceIndexAll, the root-only aggregate index task.")
            .contains("Use the leading ':' from root; otherwise Gradle can run every javaReferenceQuery task in root and subprojects.")
            .contains("What this file references: ./gradlew -q :javaReferenceQuery --sql \"select target_project, target_path, target_type from java_references where source_path = 'app/src/main/java/app/App.java'\"")
            .contains("Who references this file: ./gradlew -q :javaReferenceQuery --sql \"select source_project, source_path from java_references where target_path = 'lib/src/main/java/lib/LibraryType.java'\"")
            .contains("Options")
            .contains("--sql")
            .contains("SQL query to run against the java_references table.");
    }

    @Test
    void javaReferenceIndex_help_describesOutputCsv() throws IOException {
        copyFixture("single-project");

        var result = gradle("help", "--task", "javaReferenceIndex");

        assertThat(result.task(":help").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("Build Java reference edge CSVs.")
            .contains("Run with --info to log per-source-set timing.");
    }

    @Test
    void javaReferenceIndexAll_help_describesAggregateOutputCsv() throws IOException {
        copyFixture("single-project");

        var result = gradle("help", "--task", "javaReferenceIndexAll");

        assertThat(result.task(":help").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("Build Java reference edge CSVs for all projects.");
    }
}
