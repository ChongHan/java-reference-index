package io.github.chonghan.javareferenceindex.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

class JavaReferenceRelocatableBuildCacheTest extends GradlePluginTestKit {
    @Test
    void javaReferenceIndex_isRelocatableWithBuildCache() throws IOException {
        Path firstProject = projectDir.resolve("first");
        Path secondProject = projectDir.resolve("second");
        Path buildCacheDirectory = projectDir.resolve("local-build-cache");
        copyFixture("single-project", firstProject);
        copyFixture("single-project", secondProject);
        enableLocalBuildCache(firstProject, buildCacheDirectory);
        enableLocalBuildCache(secondProject, buildCacheDirectory);

        var firstResult = gradle(
            firstProject,
            "--build-cache",
            "javaReferenceIndex"
        );
        var secondResult = gradle(
            secondProject,
            "--build-cache",
            "javaReferenceIndex"
        );

        assertThat(firstResult.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(secondResult.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.FROM_CACHE);
        assertThat(referencesCsv(secondProject))
            .containsExactly(
                "source_project,source_path,target_kind,target_project,target_path,target_type",
                ":,src/main/java/example/App.java,source,:,src/main/java/example/Helper.java,example.Helper"
            );
    }
}
