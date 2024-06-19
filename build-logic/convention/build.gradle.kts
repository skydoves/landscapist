plugins {
  `kotlin-dsl`
}

group = "com.skydoves.landscapist.buildlogic"

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
  compileOnly(libs.android.gradlePlugin)
  compileOnly(libs.kotlin.gradlePlugin)
  compileOnly(libs.compose.compiler.gradlePlugin)
  compileOnly(libs.spotless.gradlePlugin)
}

gradlePlugin {
  plugins {
    register("androidApplicationCompose") {
      id = "landscapist.application.compose"
      implementationClass = "AndroidApplicationComposeConventionPlugin"
    }
    register("androidLibraryCompose") {
      id = "landscapist.library.compose"
      implementationClass = "AndroidLibraryComposeConventionPlugin"
    }
    register("composeMultiplatformLibrary") {
      id = "landscapist.library.compose.multiplatform"
      implementationClass = "ComposeMultiplatformLibraryConventionPlugin"
    }
    register("spotless") {
      id = "landscapist.spotless"
      implementationClass = "SpotlessConventionPlugin"
    }
  }
}
