plugins {
    alias(libs.plugins.nmcp.aggregation)
}

group = "io.github.chonghan"
version = providers.gradleProperty("releaseVersion").orElse("0.1.0-SNAPSHOT").get()

subprojects {
    group = rootProject.group
    version = rootProject.version
}

nmcpAggregation {
    centralPortal {
        username = providers.gradleProperty("mavenCentralUsername")
            .orElse(providers.environmentVariable("MAVEN_CENTRAL_USERNAME"))
            .getOrElse("")
        password = providers.gradleProperty("mavenCentralPassword")
            .orElse(providers.environmentVariable("MAVEN_CENTRAL_PASSWORD"))
            .getOrElse("")
        publishingType = "USER_MANAGED"
        publicationName = "java-reference-index:${project.version}"
    }
}

dependencies {
    nmcpAggregation(project(":reference-index-gradle-plugin"))
}
