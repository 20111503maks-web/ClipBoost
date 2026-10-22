pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ClipBoost"

// :core — pure Kotlin (no Android): analysis math, planner, job model. Testable on any JVM.
// :app  — Android client: UI, Media3 pipeline, background service.
include(":core")
include(":app")
