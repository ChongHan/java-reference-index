group = "io.github.chonghan"
version = providers.gradleProperty("releaseVersion").orElse("0.1.0-SNAPSHOT").get()

subprojects {
    group = rootProject.group
    version = rootProject.version
}
