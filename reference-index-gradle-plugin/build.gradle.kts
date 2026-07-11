import org.gradle.plugin.compatibility.compatibility

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.gradle.plugin.publish)
    id("com.gradleup.nmcp")
    alias(libs.plugins.shadow)
    signing
    alias(libs.plugins.java.reference.index)
}

group = "io.github.chonghan"
version = providers.gradleProperty("releaseVersion").orElse("0.1.0-SNAPSHOT").get()

val integrationTest = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output + configurations.testRuntimeClasspath.get()
    runtimeClasspath += output + compileClasspath + files(tasks.pluginUnderTestMetadata)
}

configurations[integrationTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[integrationTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

gradlePlugin {
    website = "https://github.com/ChongHan/java-reference-index"
    vcsUrl = "https://github.com/ChongHan/java-reference-index.git"

    plugins {
        create("javaReferenceIndex") {
            id = "io.github.chonghan.java-reference-index"
            displayName = "Java Reference Index"
            description = "Indexes Java source references for code navigation and blast-radius analysis."
            tags = listOf("java", "references", "static-analysis", "jdt", "duckdb")
            implementationClass = "io.github.chonghan.javareferenceindex.gradle.JavaReferenceIndexPlugin"
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

dependencies {
    implementation(project(":reference-index-core"))
    implementation(project(":reference-index-csv"))
    implementation(libs.duckdb.jdbc)

    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// TestKit builds launch nested Gradle workers and share a TestKit cache. Multiple test
// JVMs contend for that cache and use more CPU while taking longer than one warm JVM.
val testMaxParallelForks = providers.gradleProperty("javaReferenceIndex.test.maxParallelForks")
    .map { it.toInt().coerceAtLeast(1) }
    .getOrElse(1)

tasks.test {
    useJUnitPlatform()
    maxParallelForks = testMaxParallelForks
}

tasks.named<Jar>("shadowJar") {
    archiveClassifier.set("")
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name = "Java Reference Index"
            description = "Builds queryable Java source reference indexes for Gradle projects."
            url = "https://github.com/ChongHan/java-reference-index"

            licenses {
                license {
                    name = "Apache License, Version 2.0"
                    url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                }
            }

            developers {
                developer {
                    id = "ChongHan"
                    name = "ChongHan"
                    url = "https://github.com/ChongHan"
                }
            }

            scm {
                connection = "scm:git:https://github.com/ChongHan/java-reference-index.git"
                developerConnection = "scm:git:https://github.com/ChongHan/java-reference-index.git"
                url = "https://github.com/ChongHan/java-reference-index"
            }
        }
    }
}

signing {
    setRequired {
        gradle.taskGraph.allTasks.any { task ->
            task.name.contains("Central")
        }
    }

    useGpgCmd()
    sign(publishing.publications)
}

val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs the Gradle plugin against pinned real-world projects."
    group = "verification"

    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    shouldRunAfter(tasks.test)
    systemProperty(
        "javaReferenceIndex.integrationTestFixtures",
        layout.projectDirectory.dir("src/integrationTest/fixtures").asFile.absolutePath
    )
    useJUnitPlatform()
}

tasks.check {
    dependsOn(integrationTestTask)
}
