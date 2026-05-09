plugins {
    id("io.github.chonghan.java-reference-index")
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.github.chonghan.java-reference-index")

    afterEvaluate {
        tasks.register("afterEvaluateMarker")
    }
}
