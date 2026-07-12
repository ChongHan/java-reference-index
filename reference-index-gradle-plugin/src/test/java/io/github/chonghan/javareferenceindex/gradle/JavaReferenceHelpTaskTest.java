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
            .contains("Schema: source_project, source_path, target_origin, target_project, target_path, reference_symbol")
            .contains("Columns: source_project/source_path identify the referencing file; target_origin is source, binary, or unresolved;")
            .contains("target_project identifies the target project or dependency; target_path is set for source targets;")
            .contains("reference_symbol is the resolved type name")
            .contains("Run from the root with :javaReferenceQuery; the leading ':' avoids running matching subproject tasks.")
            .contains("Use -q for clean CSV output. The root query refreshes indexes automatically.")
            .contains("Example: ./gradlew -q :javaReferenceQuery -Psql=\"select * from java_references limit 20\"")
            .contains("References from a file: ./gradlew -q :javaReferenceQuery -Psql=\"select target_project, target_path, reference_symbol from java_references where source_path = 'app/src/main/java/app/App.java'\"")
            .contains("References to a file: ./gradlew -q :javaReferenceQuery -Psql=\"select distinct source_project, source_path from java_references where target_path = 'lib/src/main/java/lib/LibraryType.java'\"")
            .doesNotContain("--sql");
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
