plugins {
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        create("javaConvention") {
            id = "buildlogic.java-convention"
            implementationClass = "buildlogic.JavaConventionPlugin"
        }
    }
}
