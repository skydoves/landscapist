/*
 * Designed and developed by 2020-2022 skydoves (Jaewoong Eum)
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

@file:Suppress("UnstableApiUsage")

package com.skydoves.landscapist

import com.android.build.api.dsl.CommonExtension
import java.io.File
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

/**
 * Configure Compose-Multiplatform-specific options
 */
internal fun Project.configureComposeMultiplatform(
  commonExtension: CommonExtension<*, *, *, *, *, *>,
  kotlinMultiplatformExtension: KotlinMultiplatformExtension,
) {
  pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

  kotlinMultiplatformExtension.apply {
    androidTarget { publishLibraryVariants("release") }
    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
      browser {
        testTask {
          enabled = false
        }
      }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    macosX64()
    macosArm64()

    @Suppress("OPT_IN_USAGE")
    applyHierarchyTemplate {
      common {
        group("jvm") {
          withAndroidTarget()
          withJvm()
        }
        group("skia") {
          withJvm()
          group("darwin") {
            group("apple") {
              group("ios") {
                withIosX64()
                withIosArm64()
                withIosSimulatorArm64()
              }
              group("macos") {
                withMacosX64()
                withMacosArm64()
              }
            }
            withJs()
          }
        }
      }
    }

    explicitApi()
    applyKotlinJsImplicitDependencyWorkaround()
  }

  commonExtension.apply {
    buildFeatures {
      compose = true
    }

    packaging {
      resources {
        excludes.add("/META-INF/{AL2.0,LGPL2.1}")
      }
    }
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