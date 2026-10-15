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

rootProject.name = "BibliotecaGO"

include(":app")
include(":core:eink")
include(":core:model")
include(":server")
include(":catalog")
include(":downloads")
include(":library")
include(":reader-epub")
include(":reader-pdf")
include(":settings")
