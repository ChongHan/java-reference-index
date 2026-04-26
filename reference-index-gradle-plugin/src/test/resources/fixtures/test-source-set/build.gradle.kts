plugins {
    java
    id("io.github.hanc.java-reference-index")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
