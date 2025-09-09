pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.4.1"
        id("org.jetbrains.kotlin.android") version "1.9.24"
        id("org.jetbrains.kotlin.kapt") version "1.9.24"
        id("androidx.navigation.safeargs.kotlin") version "2.9.3"
        id("com.google.dagger.hilt.android") version "2.51.1"
        id("io.sentry.android.gradle") version "4.7.1"

    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "UV Recognize Medications"
include(":app")
