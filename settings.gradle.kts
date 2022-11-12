@file:Suppress("UnstableApiUsage")

pluginManagement {
  includeBuild("build-logic")
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
    maven(url = "https://plugins.gradle.org/m2/")
  }
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven(url = "https://plugins.gradle.org/m2/")
  }
}

rootProject.name = "LandscapistDemo"
include(":app")
include(":bom")
include(":landscapist")
include(":landscapist-animation")
include(":landscapist-palette")
include(":landscapist-placeholder")
include(":landscapist-transformation")
include(":coil")
include(":glide")
include(":fresco")
include(":fresco-websupport")
include(":benchmark-landscapist")
include(":benchmark-landscapist-app")
