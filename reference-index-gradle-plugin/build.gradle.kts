plugins {
    `java-gradle-plugin`
}

val integrationTest = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output + configurations.testRuntimeClasspath.get()
    runtimeClasspath += output + compileClasspath + files(tasks.pluginUnderTestMetadata)
}

configurations[integrationTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[integrationTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

gradlePlugin {
    plugins {
        create("javaReferenceIndex") {
            id = "io.github.hanc.java-reference-index"
            implementationClass = "io.github.hanc.javareferenceindex.gradle.JavaReferenceIndexPlugin"
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

tasks.test {
    useJUnitPlatform()
}

tasks.register<Test>("integrationTest") {
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
