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

        // Ktor for networking
        implementation(libs.ktor.core)

        // Okio for buffered I/O and disk cache
        api(libs.okio)

        // AtomicFU for thread-safe operations
        implementation(libs.atomicfu)
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
  }
}

tasks.withType<KotlinJvmCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
    freeCompilerArgs.addAll(
      "-Xexplicit-api=strict",
      "-opt-in=kotlin.RequiresOptIn",
      "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
    )
  }
}
