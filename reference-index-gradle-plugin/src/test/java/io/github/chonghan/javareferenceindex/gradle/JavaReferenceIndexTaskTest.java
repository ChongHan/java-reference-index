package io.github.chonghan.javareferenceindex.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void javaReferenceIndex_afterAllSourcesAreRemoved_replacesStaleCsvWithHeader() throws IOException {
        copyFixture("single-project");

        var initialResult = gradle("javaReferenceIndex");
        Files.delete(projectDir.resolve("src/main/java/example/App.java"));
        Files.delete(projectDir.resolve("src/main/java/example/Helper.java"));
        var changedResult = gradle("javaReferenceIndex");

        assertThat(initialResult.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(changedResult.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(referencesCsv(projectDir))
            .containsExactly("source_project,source_path,target_origin,target_project,target_path,reference_symbol");
    }

    @Test
    void javaReferenceIndex_afterSourceSetIsRemoved_deletesObsoleteCsv() throws IOException {
        copyFixture("single-project");
        Path buildScript = projectDir.resolve("build.gradle.kts");
        String originalBuildScript = Files.readString(buildScript);
        Files.writeString(
            buildScript,
            """

            sourceSets.create("extra")
            """,
            StandardOpenOption.APPEND
        );
        Path extraSource = projectDir.resolve("src/extra/java/example/Extra.java");
        Files.createDirectories(extraSource.getParent());
        Files.writeString(extraSource, "package example; public class Extra {}");

        var initialResult = gradle("javaReferenceIndex");
        assertThat(projectDir.resolve("build/reference-index/extra-references.csv")).isRegularFile();
        Files.writeString(buildScript, originalBuildScript);
        var changedResult = gradle("javaReferenceIndex");

        assertThat(initialResult.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(changedResult.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(projectDir.resolve("build/reference-index/main-references.csv")).isRegularFile();
        assertThat(projectDir.resolve("build/reference-index/extra-references.csv")).doesNotExist();
    }

    @Test
    void javaReferenceIndex_afterDependencySourceMoves_reindexesTargetPath() throws IOException {
        copyFixture("multi-project");

        var initialResult = gradle(":app:javaReferenceIndex");
        assertThat(referencesCsv(projectDir.resolve("app")))
            .contains(":app,app/src/main/java/app/App.java,source,:lib,lib/src/main/java/lib/LibraryType.java,lib.LibraryType");
        Path originalSource = projectDir.resolve("lib/src/main/java/lib/LibraryType.java");
        Path movedSource = projectDir.resolve("lib/src/main/java/moved/LibraryType.java");
        Files.createDirectories(movedSource.getParent());
        Files.move(originalSource, movedSource);
        var changedResult = gradle(":app:javaReferenceIndex");

        assertThat(initialResult.task(":app:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(changedResult.task(":app:javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(referencesCsv(projectDir.resolve("app")))
            .containsExactly(
                "source_project,source_path,target_origin,target_project,target_path,reference_symbol",
                ":app,app/src/main/java/app/App.java,source,:lib,lib/src/main/java/moved/LibraryType.java,lib.LibraryType"
            );
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
    void javaReferenceIndex_usesJavaCompileLanguageSettingsAndEncoding() throws IOException {
        copyFixture("single-project");
        Path buildScript = projectDir.resolve("build.gradle.kts");
        String originalBuildScript = Files.readString(buildScript);
        Files.writeString(
            buildScript,
            originalBuildScript + """

            tasks.withType<JavaCompile>().configureEach {
                sourceCompatibility = "1.8"
                targetCompatibility = "1.8"
                options.encoding = "UTF-16"
            }
            """
        );
        Files.delete(projectDir.resolve("src/main/java/example/Helper.java"));
        Files.writeString(
            projectDir.resolve("src/main/java/example/_.java"),
            "package example; public class _ {}",
            StandardCharsets.UTF_16
        );
        Files.writeString(
            projectDir.resolve("src/main/java/example/App.java"),
            "package example; public class App { private final _ value = new _(); }",
            StandardCharsets.UTF_16
        );

        var sourceLevelResult = gradle("javaReferenceIndex");
        Files.writeString(
            buildScript,
            originalBuildScript + """

            tasks.withType<JavaCompile>().configureEach {
                sourceCompatibility = "21"
                targetCompatibility = "21"
                options.release.set(8)
                options.encoding = "UTF-16"
            }
            """
        );
        var releaseResult = gradle("javaReferenceIndex");

        assertThat(sourceLevelResult.task(":compileJava").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(sourceLevelResult.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(releaseResult.task(":compileJava").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(releaseResult.task(":javaReferenceIndex").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(referencesCsv(projectDir))
            .containsExactly(
                "source_project,source_path,target_origin,target_project,target_path,reference_symbol",
                ":,src/main/java/example/App.java,source,:,src/main/java/example/_.java,example._"
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
