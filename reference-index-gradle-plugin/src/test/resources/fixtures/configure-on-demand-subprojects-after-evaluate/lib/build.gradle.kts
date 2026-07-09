plugins {
    java
    id("io.github.chonghan.java-reference-index")
}

afterEvaluate {
    tasks.register("afterEvaluateMarker")
}
