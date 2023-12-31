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
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Configure Compose-Multiplatform-specific options
 */
internal fun Project.configureComposeMultiplatform(
  commonExtension: CommonExtension<*, *, *, *, *>,
  kotlinMultiplatformExtension: KotlinMultiplatformExtension,
) {
  val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

  kotlinMultiplatformExtension.apply {
    androidTarget { publishLibraryVariants("release") }
    jvm("desktop")
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()
    js(IR) {
      browser()
    }
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
  }

  commonExtension.apply {
    buildFeatures {
      compose = true
    }

    composeOptions {
      kotlinCompilerExtensionVersion =
        libs.findVersion("androidxComposeCompiler").get().toString()
    }

    packaging {
      resources {
        excludes.add("/META-INF/{AL2.0,LGPL2.1}")
      }
    }
  }
}

private fun Project.buildComposeMetricsParameters(): List<String> {
  val metricParameters = mutableListOf<String>()
  val enableMetricsProvider = project.providers.gradleProperty("enableComposeCompilerMetrics")
  val enableMetrics = (enableMetricsProvider.orNull == "true")
  if (enableMetrics) {
    val metricsFolder = File(project.buildDir, "compose-metrics")
    metricParameters.add("-P")
    metricParameters.add(
      "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=${metricsFolder.absolutePath}/compose_metrics"
    )
  }

  val enableReportsProvider = project.providers.gradleProperty("enableComposeCompilerReports")
  val enableReports = (enableReportsProvider.orNull == "true")
  if (enableReports) {
    val reportsFolder = File(project.buildDir, "compose-reports")
    metricParameters.add("-P")
    metricParameters.add(
      "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${reportsFolder.absolutePath}/compose_metrics"
    )
  }
  return metricParameters.toList()
}
