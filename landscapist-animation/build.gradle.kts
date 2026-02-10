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
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
  id("landscapist.library.compose.multiplatformWasm")
  id("landscapist.spotless")
}

apply(from = "${rootDir}/scripts/publish-module.gradle.kts")

mavenPublishing {
  val artifactId = "landscapist-animation"
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
        api(project(":landscapist"))

        implementation(libs.jetbrains.compose.ui)
        implementation(libs.jetbrains.compose.runtime)
        implementation(libs.jetbrains.compose.foundation)
      }
    }

    androidMain {
      dependencies {
        implementation(libs.androidx.core.ktx)
      }
    }
  }

  targets.configureEach {
    compilations.configureEach {
      compileTaskProvider.configure {
        compilerOptions {
          // https://youtrack.jetbrains.com/issue/KT-61573
          freeCompilerArgs.add("-Xexpect-actual-classes")
        }
      }
    }
  }
}

android {
  namespace = "com.skydoves.landscapist.animation"
  compileSdk = Configuration.compileSdk

  defaultConfig {
    minSdk = Configuration.minSdk
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
}

baselineProfile {
  baselineProfileOutputDir = "."
  filter {
    include("com.skydoves.landscapist.animation.**")
  }
}

tasks.withType<KotlinJvmCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
    freeCompilerArgs.addAll(
      "-Xexplicit-api=strict",
      "-opt-in=kotlin.RequiresOptIn",
      "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
      "-opt-in=com.skydoves.landscapist.InternalLandscapistApi",
    )
  }
}
