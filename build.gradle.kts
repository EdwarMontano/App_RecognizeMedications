// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {

}

subprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.squareup" && requested.name == "javapoet") {
                useVersion("1.13.0")
                because("Fix NoSuchMethodError: ClassName.canonicalName() from older Javapoet used by Hilt worker")
            }
        }
    }
}