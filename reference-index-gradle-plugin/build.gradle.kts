plugins {
    `java-gradle-plugin`
    alias(libs.plugins.gradle.plugin.publish)
    alias(libs.plugins.shadow)
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
    website = "https://github.com/ChongHan/java-reference-index"
    vcsUrl = "https://github.com/ChongHan/java-reference-index.git"

    plugins {
        create("javaReferenceIndex") {
            id = "io.github.chonghan.java-reference-index"
            displayName = "Java Reference Index"
            description = "Builds queryable Java source reference indexes for Gradle projects."
            tags = listOf("java", "references", "analysis", "jdt", "duckdb")
            implementationClass = "io.github.chonghan.javareferenceindex.gradle.JavaReferenceIndexPlugin"
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

tasks.named<Jar>("shadowJar") {
    archiveClassifier.set("")
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
