plugins {
    java
    id("io.github.chonghan.java-reference-index") version "0.1.8"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
