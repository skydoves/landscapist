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

import com.github.skydoves.landscapist.Configuration

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  id("landscapist.application.compose")
  id("landscapist.spotless")
  id(libs.plugins.hilt.get().pluginId)
  kotlin("kapt")
}

android {
  compileSdk = Configuration.compileSdk
  defaultConfig {
    applicationId = "com.github.skydoves.landscapistdemo"
    minSdk = Configuration.minSdk
    targetSdk = Configuration.targetSdk
    versionCode = Configuration.versionCode
    versionName = Configuration.versionName
    multiDexEnabled = true
  }
}

dependencies {
  // material
  implementation(libs.androidx.material)

  // androidx jetpack
  implementation(libs.androidx.core.ktx)

  // hilt
  implementation(libs.hilt.android)
  kapt(libs.hilt.compiler)

  // compose
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling)
  implementation(libs.androidx.compose.runtime)
  implementation(libs.androidx.compose.material)
  implementation(libs.androidx.compose.material.iconsExtended)


  implementation(libs.androidx.compose.foundation.layout)
  implementation(libs.androidx.compose.animation)
  implementation(libs.androidx.compose.constraintlayout)

  implementation("androidx.multidex:multidex:2.0.1")

  // landscapist
  implementation(project(":fresco"))
  implementation(project(":fresco-websupport"))
  implementation(project(":glide"))
  implementation(project(":coil"))
}
