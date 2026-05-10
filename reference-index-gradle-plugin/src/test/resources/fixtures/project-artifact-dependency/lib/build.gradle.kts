val apiSourceSet = sourceSets.create("api")

val apiJar by tasks.registering(Jar::class) {
    archiveClassifier = "api"
    from(apiSourceSet.output)
}

configurations.create("apiArtifact") {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add("apiArtifact", apiJar)
}
