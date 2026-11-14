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
        mavenLocal { content { includeGroup("org.robolectric") } }
        google()
        mavenCentral()
    }
}
rootProject.name = "ControlIPs"
include(":app")
