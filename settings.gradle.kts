pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "MobiMon"
include(":app")

include(":core:core-domain", ":core:core-database", ":core:core-vss", ":core:core-ui")
include(":feature:feature-pet", ":feature:feature-quest", ":feature:feature-vehicle-info")
include(":feature:feature-auth")
include(":feature:feature-customization")

include(":core:core-navigation", ":core:core-presentation")

include(":core:core-auth")
