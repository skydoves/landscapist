/*
 * Designed and developed by 2020-2023 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.github.skydoves.landscapist.Configuration

plugins {
  id("landscapist.library.compose.multiplatformWasm")
  id("landscapist.spotless")
}

apply(from = "${rootDir}/scripts/publish-module.gradle.kts")

mavenPublishing {
  val artifactId = "landscapist"
  coordinates(
    Configuration.artifactGroup,
    artifactId,
    rootProject.extra.get("libVersion").toString()
  )

  pom {
    name.set(artifactId)
  }
}

kotlin {
  sourceSets {
    all {
      languageSettings.optIn("kotlin.RequiresOptIn")
      languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
      languageSettings.optIn("com.skydoves.landscapist.InternalLandscapistApi")
    }
    commonMain {
      dependencies {
        implementation(compose.ui)
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(libs.kotlinx.coroutines.core)
      }
    }

    androidMain {
      dependencies {
        implementation(libs.androidx.core.ktx)
      }
    }
  }
}

android {
  namespace = "com.skydoves.landscapist"
  compileSdk = Configuration.compileSdk
  defaultConfig {
    minSdk = Configuration.minSdk
  }
}

baselineProfile {
  baselineProfileOutputDir = "."
  filter {
    include("com.skydoves.landscapist.**")
    exclude("com.skydoves.landscapist.glide.**")
    exclude("com.skydoves.landscapist.fresco.**")
    exclude("com.skydoves.landscapist.coil.**")
    exclude("com.skydoves.landscapist.animation.**")
    exclude("com.skydoves.landscapist.palette.**")
    exclude("com.skydoves.landscapist.placeholder.**")
    exclude("com.skydoves.landscapist.transformation.**")
    exclude("com.skydoves.benchmark.**")
  }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  kotlinOptions {
    jvmTarget = "1.8"
    freeCompilerArgs += listOf(
      "-Xexplicit-api=strict",
      "-opt-in=kotlin.RequiresOptIn",
      "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
      "-opt-in=com.skydoves.landscapist.InternalLandscapistApi",
    )
  }
}
