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
  id("landscapist.library.kmp")
  id("landscapist.spotless")
}

apply(from = "${rootDir}/scripts/publish-module.gradle.kts")

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

mavenPublishing {
  val artifactId = "landscapist-core"
  coordinates(
    Configuration.artifactGroup,
    artifactId,
    rootProject.extra.get("libVersion").toString()
  )

  pom {
    name.set(artifactId)
    description.set(
      "A lightweight, Kotlin Multiplatform image loading core library with networking, " +
        "caching, and decoding capabilities."
    )
  }
}

kotlin {
  sourceSets {
    all {
      languageSettings.optIn("kotlin.RequiresOptIn")
      languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
    commonMain {
      dependencies {
        // Coroutines
        implementation(libs.kotlinx.coroutines.core)

        // Compose runtime annotation for stability markers (@Stable, @Immutable)
        implementation(libs.compose.runtime.annotation)

        // Ktor for networking
        implementation(libs.ktor.core)

        // Okio for buffered I/O and disk cache
        api(libs.okio)

        // AtomicFU for thread-safe operations
        implementation(libs.atomicfu)
      }
    }

    commonTest {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.ktor.mock)
      }
    }

    androidMain {
      dependencies {
        implementation(libs.ktor.okhttp)
        implementation(libs.androidx.core.ktx)
      }
    }

    appleMain {
      dependencies {
        implementation(libs.ktor.engine.darwin)
      }
    }

    desktopMain {
      dependencies {
        implementation(libs.ktor.engine.cio)

        // ImageIO's JPEG reader cannot scale while it decodes, so a thumbnail costs a full decode.
        // Skia can, and every Compose Multiplatform application already resolves skiko. compileOnly
        // keeps it out of the published dependencies: a plain JVM consumer without it on the
        // classpath falls back to ImageIO, which is what this module used to do everywhere.
        compileOnly(libs.skiko)
      }
    }

    val desktopTest by getting {
      dependencies {
        // The decoder tests have to exercise the Skia path, not just the ImageIO fallback, so the
        // API jar and the host's native runtime both have to be on the test classpath.
        implementation(libs.skiko)
        runtimeOnly(
          "org.jetbrains.skiko:skiko-awt-runtime-${skikoHostTarget()}:${libs.versions.skiko.get()}",
        )
      }
    }

    wasmJsMain {
      dependencies {
        implementation(libs.ktor.engine.js)
      }
    }
  }

  targets.configureEach {
    compilations.configureEach {
      compilerOptions.configure {
        // https://youtrack.jetbrains.com/issue/KT-61573
        freeCompilerArgs.add("-Xexpect-actual-classes")
      }
    }
  }
}

android {
  namespace = "com.skydoves.landscapist.core"
  compileSdk = Configuration.compileSdk

  defaultConfig {
    minSdk = Configuration.minSdk
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")
  }
}

tasks.withType<KotlinJvmCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
    // Only apply explicit API mode to main sources, not tests
    if (!name.contains("Test")) {
      freeCompilerArgs.addAll(
        "-Xexplicit-api=strict",
      )
    }
    freeCompilerArgs.addAll(
      "-opt-in=kotlin.RequiresOptIn",
      "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
    )
  }
}
