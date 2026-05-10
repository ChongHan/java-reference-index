plugins {
    java
    id("io.github.chonghan.java-reference-index")
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.github.chonghan.java-reference-index")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }
}
