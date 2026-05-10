plugins {
    java
    id("io.github.chonghan.java-reference-index")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val lazyJarInput by tasks.registering

tasks.register<Jar>("generatedJar") {
    dependsOn(lazyJarInput)
}

afterEvaluate {
    project.getAllTasks(true)
}
