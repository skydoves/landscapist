@file:Suppress("UnstableApiUsage")
pluginManagement {
  includeBuild("build-logic")
  repositories {
    google()
    gradlePluginPortal()
    mavenCentral()
    maven(url = "https://plugins.gradle.org/m2/")
    maven(url = "https://maven.pkg.jetbrains.space/public/p/compose/dev")
  }
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
  repositories {
    google()
    mavenCentral()
    maven(url = "https://plugins.gradle.org/m2/")
    maven(url = "https://maven.pkg.jetbrains.space/public/p/compose/dev")
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
include(":coil3")
include(":glide")
include(":fresco")
include(":fresco-websupport")
include(":benchmark-landscapist")
include(":benchmark-landscapist-app")
