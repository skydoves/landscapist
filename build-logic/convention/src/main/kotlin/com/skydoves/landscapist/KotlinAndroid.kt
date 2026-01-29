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
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Configure base Kotlin with Android options for library modules
 */
internal fun Project.configureKotlinAndroid(
  libraryExtension: LibraryExtension,
) {
  val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

  libraryExtension.apply {
    compileOptions {
      sourceCompatibility = JavaVersion.VERSION_17
      targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
      abortOnError = false
    }
  }

  configureKotlinCompile(libs)
}

/**
 * Configure base Kotlin with Android options for application modules
 */
internal fun Project.configureKotlinAndroid(
  appExtension: BaseAppModuleExtension,
) {
  val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

  appExtension.apply {
    compileOptions {
      sourceCompatibility = JavaVersion.VERSION_17
      targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
      abortOnError = false
    }
  }

  configureKotlinCompile(libs)
}

private fun Project.configureKotlinCompile(libs: org.gradle.api.artifacts.VersionCatalog) {
  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>() {
    compilerOptions {
      jvmTarget.set(JvmTarget.fromTarget(libs.findVersion("jvmTarget").get().toString()))
      freeCompilerArgs.addAll(
        listOf(
          "-opt-in=kotlin.RequiresOptIn",
          "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
          "-opt-in=com.skydoves.landscapist.InternalLandscapistApi",
        )
      )
    }
  }
}