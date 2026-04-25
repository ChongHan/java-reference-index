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
