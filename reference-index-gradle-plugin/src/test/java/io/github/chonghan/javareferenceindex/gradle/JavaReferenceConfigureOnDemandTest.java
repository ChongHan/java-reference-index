package io.github.chonghan.javareferenceindex.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

class JavaReferenceConfigureOnDemandTest extends GradlePluginTestKit {
    @Test
    void javaReferenceQuery_fromRootProjectWithNestedSubprojects_queriesNestedSubprojectCsvFiles() throws IOException {
        copyFixture("nested-subprojects");

        var result = gradle(
            ":javaReferenceQuery",
            "--sql",
            "select source_project, target_project, target_path from java_references where target_kind = 'source' order by source_project"
        );

        assertThat(result.task(":apps:service:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":libs:shared:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":javaReferenceQuery").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("source_project,target_project,target_path")
            .contains(":apps:service,:libs:shared,libs/shared/src/main/java/shared/SharedType.java");
        assertThat(referencesCsv(projectDir.resolve("apps/service")))
            .containsExactly(
                "source_project,source_path,target_kind,target_project,target_path,target_type",
                ":apps:service,apps/service/src/main/java/service/App.java,source,:libs:shared,libs/shared/src/main/java/shared/SharedType.java,shared.SharedType"
            );
    }

    @Test
    void javaReferenceQuery_fromRootProjectWithConfigureOnDemand_indexesSubprojectsOnDemand() throws IOException {
        copyFixture("multi-project");

        var result = gradle(
            ":javaReferenceQuery",
            "--sql",
            "select source_project, target_project, target_path from java_references where target_kind = 'source' order by target_path"
        );

        assertThat(result.task(":app:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":lib:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":javaReferenceQuery").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("source_project,target_project,target_path")
            .contains(":app,:lib,lib/src/main/java/lib/LibraryType.java");
        assertThat(projectDir.resolve("app/build/reference-index/main-references.csv")).isRegularFile();
        assertThat(projectDir.resolve("lib/build/reference-index/main-references.csv")).isRegularFile();
        assertThat(referencesCsv(projectDir.resolve("app")))
            .containsExactly(
                "source_project,source_path,target_kind,target_project,target_path,target_type",
                ":app,app/src/main/java/app/App.java,source,:lib,lib/src/main/java/lib/LibraryType.java,lib.LibraryType"
            );
    }

    @Test
    void javaReferenceQuery_fromRootProjectWithConfigureOnDemandAndIncludedBuildPlugin_indexesSubprojectsOnDemand()
        throws IOException {
        copyFixture("configure-on-demand-included-build-plugin");

        var result = gradle(
            ":javaReferenceQuery",
            "--sql",
            "select source_project, target_project, target_path from java_references where target_kind = 'source' order by target_path"
        );

        assertThat(result.task(":app:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":lib:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":javaReferenceQuery").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("source_project,target_project,target_path")
            .contains(":app,:lib,lib/src/main/java/lib/LibraryType.java");
    }

    @Test
    void javaReferenceQuery_fromRootProjectWithConfigureOnDemandAndSubprojectsAfterEvaluate_indexesSubprojectsOnDemand()
        throws IOException {
        copyFixture("configure-on-demand-subprojects-after-evaluate");

        var result = gradle(
            ":javaReferenceQuery",
            "--sql",
            "select source_project, target_project, target_path from java_references where target_kind = 'source' order by target_path"
        );

        assertThat(result.task(":app:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":lib:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":javaReferenceQuery").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("source_project,target_project,target_path")
            .contains(":app,:lib,lib/src/main/java/lib/LibraryType.java");
    }

    @Test
    void javaReferenceQuery_fromRootProjectWithConfigureOnDemandAndSubprojectBuildScripts_indexesSubprojectsOnDemand()
        throws IOException {
        copyFixture("configure-on-demand-subproject-build-scripts");

        var result = gradle(
            ":javaReferenceQuery",
            "--sql",
            "select source_project, target_project, target_path from java_references where target_kind = 'source' order by target_path"
        );

        assertThat(result.task(":app:javaReferenceIndex")).isNotNull();
        assertThat(result.task(":lib:javaReferenceIndex")).isNotNull();
        assertThat(result.task(":app:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":lib:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":javaReferenceQuery").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("source_project,target_project,target_path")
            .contains(":app,:lib,lib/src/main/java/lib/LibraryType.java");
    }
}
