import org.gradle.api.tasks.compile.JavaCompile

plugins {
    java
    id("io.github.chonghan.java-reference-index")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val generatedDir = layout.buildDirectory.dir("generated-src")

sourceSets {
    create("generated") {
        java.srcDir(generatedDir)
        compileClasspath += sourceSets.main.get().output
    }
}

dependencies {
    testImplementation(sourceSets.named("generated").get().output)
}

val generateFixtureSource = tasks.register("generateFixtureSource") {
    val outputFile = generatedDir.map { it.file("example/generated/GeneratedType.java") }
    outputs.file(outputFile)

    doLast {
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                """
                package example.generated;

                public class GeneratedType {
                }
                """.trimIndent()
            )
        }
    }
}

tasks.named<JavaCompile>("compileGeneratedJava") {
    dependsOn(generateFixtureSource)
}

tasks.named<JavaCompile>("compileTestJava") {
    dependsOn("compileGeneratedJava")
}
