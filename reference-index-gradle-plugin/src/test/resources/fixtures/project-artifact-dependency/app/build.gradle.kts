plugins {
    java
    id("io.github.chonghan.java-reference-index")
}

dependencies {
    implementation(project(path = ":lib", configuration = "apiArtifact"))
}
