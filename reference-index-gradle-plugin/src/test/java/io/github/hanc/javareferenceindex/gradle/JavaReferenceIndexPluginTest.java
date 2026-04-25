package io.github.hanc.javareferenceindex.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaReferenceIndexPluginTest {
    @TempDir
    Path projectDir;

    @Test
    void indexJavaReferences_withSingleProject_printsSourceReferences() throws IOException {
        writeSingleProjectFixture();

        var result = gradle("indexJavaReferences");

        assertThat(result.task(":indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("project=:")
            .contains("sourceSet=main")
            .contains("file=example.App")
            .contains("sourceRef=example.Helper");
    }

    @Test
    void indexJavaReferences_withProjectDependency_printsDependentProjectSourceReference() throws IOException {
        writeMultiProjectFixture();

        var result = gradle(":app:indexJavaReferences");

        assertThat(result.task(":app:indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("project=:app")
            .contains("file=app.App")
            .contains("sourceRef=lib.LibraryType")
            .contains("targetProject=:lib");
    }

    @Test
    void indexJavaReferences_withExternalDependency_printsBinaryReference() throws IOException {
        writeAgronaFixture();

        var result = gradle("indexJavaReferences");

        assertThat(result.task(":indexJavaReferences").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput())
            .contains("file=example.App")
            .contains("binaryRef=org.agrona.collections.IntArrayList")
            .contains("agrona");
    }

    private org.gradle.testkit.runner.BuildResult gradle(String... arguments) {
        return GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(arguments)
            .forwardOutput()
            .build();
    }

    private void writeSingleProjectFixture() throws IOException {
        write("settings.gradle.kts", """
            pluginManagement {
                repositories {
                    mavenCentral()
                    gradlePluginPortal()
                }
            }

            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    mavenCentral()
                }
            }
            """);
        write("build.gradle.kts", """
            plugins {
                java
                id("io.github.hanc.java-reference-index")
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(21)
                }
            }
            """);
        write("src/main/java/example/App.java", """
            package example;

            public class App {
                private Helper helper;
            }
            """);
        write("src/main/java/example/Helper.java", """
            package example;

            public class Helper {
            }
            """);
    }

    private void writeMultiProjectFixture() throws IOException {
        write("settings.gradle.kts", """
            pluginManagement {
                repositories {
                    mavenCentral()
                    gradlePluginPortal()
                }
            }

            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    mavenCentral()
                }
            }

            include("app")
            include("lib")
            """);
        write("build.gradle.kts", """
            plugins {
                java
                id("io.github.hanc.java-reference-index") apply false
            }

            subprojects {
                apply(plugin = "java")
                apply(plugin = "io.github.hanc.java-reference-index")

                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(21)
                    }
                }
            }
            """);
        write("app/build.gradle.kts", """
            dependencies {
                implementation(project(":lib"))
            }
            """);
        write("app/src/main/java/app/App.java", """
            package app;

            import lib.LibraryType;

            public class App {
                private LibraryType libraryType;
            }
            """);
        write("lib/src/main/java/lib/LibraryType.java", """
            package lib;

            public class LibraryType {
            }
            """);
    }

    private void writeAgronaFixture() throws IOException {
        write("settings.gradle.kts", """
            pluginManagement {
                repositories {
                    mavenCentral()
                    gradlePluginPortal()
                }
            }

            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    mavenCentral()
                }
            }
            """);
        write("build.gradle.kts", """
            plugins {
                java
                id("io.github.hanc.java-reference-index")
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(21)
                }
            }

            dependencies {
                implementation("org.agrona:agrona:2.4.1")
            }
            """);
        write("src/main/java/example/App.java", """
            package example;

            import org.agrona.collections.IntArrayList;

            public class App {
                private IntArrayList values;
            }
            """);
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = projectDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content.stripIndent());
    }
}
