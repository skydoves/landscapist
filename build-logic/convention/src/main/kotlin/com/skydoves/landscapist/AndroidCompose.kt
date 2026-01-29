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

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/**
 * Configure Compose-specific options for library modules
 */
internal fun Project.configureAndroidCompose(
  libraryExtension: LibraryExtension,
) {
  pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
  pluginManager.apply("com.github.skydoves.compose.stability.analyzer")

  libraryExtension.apply {
    buildFeatures {
      compose = true
    }

    packaging {
      resources {
        excludes.add("/META-INF/{AL2.0,LGPL2.1}")
      }
    }
  }

  configureComposeCompiler()
}

/**
 * Configure Compose-specific options for application modules
 */
internal fun Project.configureAndroidCompose(
  appExtension: BaseAppModuleExtension,
) {
  pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
  pluginManager.apply("com.github.skydoves.compose.stability.analyzer")

  appExtension.apply {
    buildFeatures {
      compose = true
    }

    packaging {
      resources {
        excludes.add("/META-INF/{AL2.0,LGPL2.1}")
      }
    }
  }

  configureComposeCompiler()
}

private fun Project.configureComposeCompiler() {
  extensions.configure<ComposeCompilerGradlePluginExtension> {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
  }

  val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

  dependencies {
    val bom = libs.findLibrary("androidx-compose-bom").get()
    add("implementation", platform(bom))
    add("androidTestImplementation", platform(bom))
  }
}
