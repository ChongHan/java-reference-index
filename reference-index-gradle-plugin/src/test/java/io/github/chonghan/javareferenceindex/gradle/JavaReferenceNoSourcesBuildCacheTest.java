package io.github.chonghan.javareferenceindex.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

class JavaReferenceNoSourcesBuildCacheTest extends GradlePluginTestKit {
    @Test
    void javaReferenceIndex_fromCacheWithNoSources_doesNotDeleteExistingReferenceCsvFiles() throws IOException {
        Path firstProject = projectDir.resolve("first");
        Path secondProject = projectDir.resolve("second");
        Path buildCacheDirectory = projectDir.resolve("local-build-cache");
        copyFixture("subproject-without-java-sources", firstProject);
        copyFixture("subproject-without-java-sources", secondProject);
        enableLocalBuildCache(firstProject, buildCacheDirectory);
        enableLocalBuildCache(secondProject, buildCacheDirectory);

        var firstResult = gradle(
            firstProject,
            "--build-cache",
            ":empty:javaReferenceIndex"
        );
        Path existingCsv = secondProject.resolve("empty/build/reference-index/main-references.csv");
        Files.createDirectories(existingCsv.getParent());
        Files.writeString(
            existingCsv,
            """
            source_project,source_path,target_kind,target_project,target_path,target_type
            :empty,empty/src/main/java/example/App.java,source,:empty,empty/src/main/java/example/Helper.java,example.Helper
            """
        );

        var secondResult = gradle(
            secondProject,
            "--build-cache",
            ":empty:javaReferenceIndex"
        );

        assertThat(firstResult.task(":empty:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(secondResult.task(":empty:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.FROM_CACHE);
        assertThat(existingCsv).isRegularFile();
    }
}
