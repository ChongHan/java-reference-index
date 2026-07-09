plugins {
    java
    id("io.github.chonghan.java-reference-index")
}

dependencies {
    implementation(project(":lib"))
}

afterEvaluate {
    tasks.register("afterEvaluateMarker")
}
