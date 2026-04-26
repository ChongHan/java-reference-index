plugins {
    `java-gradle-plugin`
}

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

    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
