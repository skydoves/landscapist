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

/** Resolves the skiko native runtime classifier (e.g. "macos-arm64") for the running host. */
fun skikoHostTarget(): String {
  val os = System.getProperty("os.name").lowercase()
  val arch = System.getProperty("os.arch").lowercase()
  val osPart = when {
    os.contains("mac") || os.contains("darwin") -> "macos"
    os.contains("windows") -> "windows"
    else -> "linux"
  }
  val archPart = if (arch.contains("aarch64") || arch.contains("arm64")) "arm64" else "x64"
  return "$osPart-$archPart"
}

plugins {
  id("landscapist.library.compose.multiplatformWasm")
  id("landscapist.spotless")
}

apply(from = "${rootDir}/scripts/publish-module.gradle.kts")

mavenPublishing {
  val artifactId = "landscapist-zoomable"
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

    val desktopTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.jetbrains.compose.ui.test.junit4)
        implementation(libs.jetbrains.compose.foundation)
        // Skiko native runtime for the host OS, required to render off-screen during the
        // desktop runComposeUiTest runs. The classifier is resolved from the running host so the
        // tests also run on CI (e.g. linux-x64).
        runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-${skikoHostTarget()}:${libs.versions.skiko.get()}")
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
  namespace = "com.skydoves.landscapist.zoomable"
  compileSdk = Configuration.compileSdk

  defaultConfig {
    minSdk = Configuration.minSdk
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
}

baselineProfile {
  filter {
    include("com.skydoves.landscapist.zoomable.**")
  }
}

tasks.withType<KotlinJvmCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
    // Only apply explicit API mode to main sources, not tests
    if (!name.contains("Test")) {
      freeCompilerArgs.add("-Xexplicit-api=strict")
    }
    freeCompilerArgs.addAll(
      "-opt-in=kotlin.RequiresOptIn",
      "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
      "-opt-in=com.skydoves.landscapist.InternalLandscapistApi",
    )
  }
}
