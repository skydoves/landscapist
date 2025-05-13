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
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Configure Compose-Multiplatform-specific options
 */
internal fun Project.configureComposeMultiplatformWasm(
  commonExtension: CommonExtension<*, *, *, *, *, *>,
  kotlinMultiplatformExtension: KotlinMultiplatformExtension,
) {
  pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

  kotlinMultiplatformExtension.apply {
    androidTarget { publishLibraryVariants("release") }
    jvm("desktop")
    jvmToolchain(11)
    
    wasmJs {
      browser {
        testTask {
          enabled = false
        }
      }
      nodejs {
        testTask {
          enabled = false
        }
      }
      binaries.library()
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
            withWasmJs()
          }
        }
      }
    }

    explicitApi()
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
