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
  id("landscapist.library.compose.multiplatform")
  id("landscapist.spotless")
}

apply(from = "${rootDir}/scripts/publish-module.gradle.kts")

mavenPublishing {
  val artifactId = "landscapist-coil3"
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
      languageSettings.optIn("coil3.annotation.ExperimentalCoilApi")
    }
    val commonMain by getting {
      dependencies {
        api(project(":landscapist"))
        api(libs.coil3)
        api(libs.coil3.network)
        api(libs.ktor.core)

        implementation(compose.ui)
        implementation(compose.runtime)
        implementation(compose.foundation)
        @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
        implementation(compose.components.resources)
      }
    }

    val androidMain by getting {
      dependencies {
        implementation(libs.androidx.core.ktx)
      }
    }

    val jvmMain by getting {
      dependencies {
        api(libs.ktor.okhttp)
      }
    }

    val iosMain by getting {
      dependencies {
        api(libs.ktor.engine.darwin)
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

  explicitApi()
  applyKotlinJsImplicitDependencyWorkaround()
}

android {
  namespace = "com.skydoves.landscapist.coil3"
  compileSdk = Configuration.compileSdk

  defaultConfig {
    minSdk = Configuration.minSdk
    consumerProguardFiles("consumer-rules.pro")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
}

baselineProfile {
  baselineProfileOutputDir = "."
  filter {
    include("com.skydoves.landscapist.coil3.**")
  }
}

// https://youtrack.jetbrains.com/issue/KT-56025
fun Project.applyKotlinJsImplicitDependencyWorkaround() {
  tasks {
    val configureJs: Task.() -> Unit = {
      dependsOn(named("jsDevelopmentLibraryCompileSync"))
      dependsOn(named("jsDevelopmentExecutableCompileSync"))
      dependsOn(named("jsProductionLibraryCompileSync"))
      dependsOn(named("jsProductionExecutableCompileSync"))
      dependsOn(named("jsTestTestDevelopmentExecutableCompileSync"))

      dependsOn(getByPath(":coil3:jsDevelopmentLibraryCompileSync"))
      dependsOn(getByPath(":coil3:jsDevelopmentExecutableCompileSync"))
      dependsOn(getByPath(":coil3:jsProductionLibraryCompileSync"))
      dependsOn(getByPath(":coil3:jsProductionExecutableCompileSync"))
      dependsOn(getByPath(":coil3:jsTestTestDevelopmentExecutableCompileSync"))
    }
    named("jsBrowserProductionWebpack").configure(configureJs)
    named("jsBrowserProductionLibraryDistribution").configure(configureJs)
    named("jsNodeProductionLibraryDistribution").configure(configureJs)

    val configureWasmJs: Task.() -> Unit = {
      dependsOn(named("wasmJsDevelopmentLibraryCompileSync"))
      dependsOn(named("wasmJsDevelopmentExecutableCompileSync"))
      dependsOn(named("wasmJsProductionLibraryCompileSync"))
      dependsOn(named("wasmJsProductionExecutableCompileSync"))
      dependsOn(named("wasmJsTestTestDevelopmentExecutableCompileSync"))

      dependsOn(getByPath(":coil3:wasmJsDevelopmentLibraryCompileSync"))
      dependsOn(getByPath(":coil3:wasmJsDevelopmentExecutableCompileSync"))
      dependsOn(getByPath(":coil3:wasmJsProductionLibraryCompileSync"))
      dependsOn(getByPath(":coil3:wasmJsProductionExecutableCompileSync"))
      dependsOn(getByPath(":coil3:wasmJsTestTestDevelopmentExecutableCompileSync"))
    }
    named("wasmJsBrowserProductionWebpack").configure(configureWasmJs)
    named("wasmJsBrowserProductionLibraryDistribution").configure(configureWasmJs)
    named("wasmJsNodeProductionLibraryDistribution").configure(configureWasmJs)
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
      "-opt-in=coil3.annotation.ExperimentalCoilApi",
    )
  }
}
