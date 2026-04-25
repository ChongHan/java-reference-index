plugins {
    java
    id("io.github.hanc.java-reference-index")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation("org.agrona:agrona:2.4.1")
}
