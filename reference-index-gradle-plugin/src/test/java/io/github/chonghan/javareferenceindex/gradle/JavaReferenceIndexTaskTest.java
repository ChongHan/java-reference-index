package io.github.chonghan.javareferenceindex.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

class JavaReferenceIndexTaskTest extends GradlePluginTestKit {
    @Test
    void javaReferenceIndex_withSingleProject_writesSourceReferences() throws IOException {
        copyFixture("single-project");

        var result = gradle("javaReferenceIndex");

        assertThat(result.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(referencesCsv(projectDir))
            .containsExactly(
                "source_project,source_path,target_origin,target_project,target_path,reference_symbol",
                ":,src/main/java/example/App.java,source,:,src/main/java/example/Helper.java,example.Helper"
            );
    }

    @Test
    void javaReferenceIndex_withDotInSourceDirectory_writesSiblingSourceReferencePath() throws IOException {
        copyFixture("dotted-package-directory");

        var result = gradle("javaReferenceIndex");

        assertThat(result.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(referencesCsv(projectDir))
            .containsExactly(
                "source_project,source_path,target_origin,target_project,target_path,reference_symbol",
                ":,src/main/java/example.with.dot/UsesHelper.java,source,:,src/main/java/example.with.dot/Helper.java,example.with.dot.Helper"
            );
    }

    @Test
    void javaReferenceIndex_withProjectDependency_writesDependentProjectSourceReference() throws IOException {
        copyFixture("multi-project");

        var result = gradle(":app:javaReferenceIndex");

        assertThat(result.task(":app:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(referencesCsv(projectDir.resolve("app")))
            .containsExactly(
                "source_project,source_path,target_origin,target_project,target_path,reference_symbol",
                ":app,app/src/main/java/app/App.java,source,:lib,lib/src/main/java/lib/LibraryType.java,lib.LibraryType"
            );
    }

    @Test
    void javaReferenceIndex_withProjectDependencyUsingCustomSourceDirectory_writesSourceReference() throws IOException {
        copyFixture("multi-project");
        var customSource = projectDir.resolve("lib/custom-sources/lib/LibraryType.java");
        Files.createDirectories(customSource.getParent());
        Files.move(projectDir.resolve("lib/src/main/java/lib/LibraryType.java"), customSource);
        Files.writeString(
            projectDir.resolve("lib/build.gradle.kts"),
            """

            sourceSets.named("main") {
                java.setSrcDirs(listOf("custom-sources"))
            }
            """,
            StandardOpenOption.APPEND
        );

        var result = gradle(":app:javaReferenceIndex");

        assertThat(result.task(":app:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(referencesCsv(projectDir.resolve("app")))
            .containsExactly(
                "source_project,source_path,target_origin,target_project,target_path,reference_symbol",
                ":app,app/src/main/java/app/App.java,source,:lib,lib/custom-sources/lib/LibraryType.java,lib.LibraryType"
            );
    }

    @Test
    void javaReferenceIndex_withProjectArtifactDependency_writesArtifactSourceReference() throws IOException {
        copyFixture("project-artifact-dependency");

        var result = gradle(":app:javaReferenceIndex");

        assertThat(result.task(":app:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(referencesCsv(projectDir.resolve("app")))
            .containsExactly(
                "source_project,source_path,target_origin,target_project,target_path,reference_symbol",
                ":app,app/src/main/java/app/App.java,source,:lib,lib/src/api/java/libapi/ArtifactType.java,libapi.ArtifactType"
            );
    }

    @Test
    void javaReferenceIndex_withRenamedProjectArtifact_writesArtifactSourceReference() throws IOException {
        copyFixture("project-artifact-dependency");
        Files.writeString(
            projectDir.resolve("lib/build.gradle.kts"),
            """

            apiJar.configure {
                archiveFileName.set("public-api.jar")
            }
            """,
            StandardOpenOption.APPEND
        );

        var result = gradle(":app:javaReferenceIndex");

        assertThat(result.task(":app:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(referencesCsv(projectDir.resolve("app")))
            .containsExactly(
                "source_project,source_path,target_origin,target_project,target_path,reference_symbol",
                ":app,app/src/main/java/app/App.java,source,:lib,lib/src/api/java/libapi/ArtifactType.java,libapi.ArtifactType"
            );
    }

    @Test
    void javaReferenceIndex_withTasksRealizedDuringAfterEvaluate_doesNotMutateTaskContainer() throws IOException {
        copyFixture("after-evaluate-task-realization");

        var result = gradle("javaReferenceIndex");

        assertThat(result.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(referencesCsv(projectDir))
            .containsExactly(
                "source_project,source_path,target_origin,target_project,target_path,reference_symbol",
                ":,src/main/java/example/App.java,source,:,src/main/java/example/Helper.java,example.Helper"
            );
    }

    @Test
    void javaReferenceIndexAll_fromRootProject_indexesAllSubprojects() throws IOException {
        copyFixture("multi-project");

        var result = gradle("javaReferenceIndexAll");

        assertThat(result.task(":javaReferenceIndexAll").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":app:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":lib:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":unused:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(referencesCsv(projectDir.resolve("app")))
            .containsExactly(
                "source_project,source_path,target_origin,target_project,target_path,reference_symbol",
                ":app,app/src/main/java/app/App.java,source,:lib,lib/src/main/java/lib/LibraryType.java,lib.LibraryType"
            );
        assertThat(projectDir.resolve("lib/build/reference-index/main-references.csv")).isRegularFile();
        assertThat(projectDir.resolve("aaa-unused/build/reference-index/main-references.csv")).isRegularFile();
    }

    @Test
    void javaReferenceIndexAll_whenReachedThroughLifecycleTask_indexesAllSubprojects() throws IOException {
        copyFixture("multi-project");
        Files.writeString(
            projectDir.resolve("build.gradle.kts"),
            """

            tasks.register("aggregateReferenceIndex") {
                dependsOn("javaReferenceIndexAll")
            }
            """,
            StandardOpenOption.APPEND
        );

        var result = gradle("aggregateReferenceIndex");

        assertThat(result.task(":javaReferenceIndexAll").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":app:javaReferenceIndex")).isNotNull();
        assertThat(result.task(":lib:javaReferenceIndex")).isNotNull();
        assertThat(result.task(":unused:javaReferenceIndex")).isNotNull();
        assertThat(result.task(":app:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":lib:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":unused:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void javaReferenceIndex_withExternalDependency_writesBinaryReference() throws IOException {
        copyFixture("external-dependency");

        var result = gradle("javaReferenceIndex");

        assertThat(result.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(referencesCsv(projectDir))
            .containsExactly(
                "source_project,source_path,target_origin,target_project,target_path,reference_symbol",
                ":,src/main/java/example/App.java,binary,org.agrona:agrona:2.4.1,,org.agrona.collections.IntArrayList"
            );
    }

    @Test
    void javaReferenceIndex_withLombokBuilder_writesBinaryAnnotationReference() throws IOException {
        copyFixture("lombok-annotation-dependency");

        var result = gradle("javaReferenceIndex");

        assertThat(result.task(":compileJava").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(referencesCsv(projectDir))
            .containsExactly(
                "source_project,source_path,target_origin,target_project,target_path,reference_symbol",
                ":,src/main/java/example/App.java,binary,org.projectlombok:lombok:1.18.32,,lombok.Builder",
                ":,src/main/java/example/UsesBuilder.java,source,:,src/main/java/example/App.java,example.App"
            );
    }

    @Test
    void javaReferenceIndex_withTestSourceSet_resolvesMainSourceReference() throws IOException {
        copyFixture("test-source-set");

        var result = gradle("javaReferenceIndex");

        assertThat(result.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(referencesCsv(projectDir, "test"))
            .containsExactly(
                "source_project,source_path,target_origin,target_project,target_path,reference_symbol",
                ":,src/test/java/example/MainTypeUsage.java,source,:,src/main/java/example/MainType.java,example.MainType"
            );
    }

    @Test
    void javaReferenceIndex_withGeneratedSourceOnTestClasspath_resolvesGeneratedSourceReference() throws IOException {
        copyFixture("generated-source-set");

        var result = gradle("javaReferenceIndex");

        assertThat(result.task(":compileGeneratedJava").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(referencesCsv(projectDir, "test"))
            .containsExactly(
                "source_project,source_path,target_origin,target_project,target_path,reference_symbol",
                ":,src/test/java/example/GeneratedTypeUsage.java,source,:,build/generated-src/example/generated/GeneratedType.java,example.generated.GeneratedType"
            );
    }

    @Test
    void javaReferenceIndex_withInfoLogging_printsPerSourceSetTiming() throws IOException {
        copyFixture("single-project");

        var result = gradle("--info", "javaReferenceIndex");

        assertThat(result.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("[java-reference-index] : main")
            .contains("files=2")
            .contains("sourceRoots=1")
            .contains("prepare=")
            .contains("index=")
            .contains("csv=")
            .contains("total=")
            .contains("sourceRefs=1")
            .contains("binaryRefs=0")
            .contains("unresolvedRefs=0");
    }
}
