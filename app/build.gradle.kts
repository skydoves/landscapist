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
  id("landscapist.application.compose")
  id("landscapist.spotless")
  id(libs.plugins.hilt.get().pluginId)
  kotlin("kapt")
}

android {
  namespace = "com.github.skydoves.landscapistdemo"
  compileSdk = Configuration.compileSdk
  defaultConfig {
    applicationId = "com.github.skydoves.landscapistdemo"
    minSdk = Configuration.minSdk24
    targetSdk = Configuration.targetSdk
    versionCode = Configuration.versionCode
    versionName = Configuration.versionName
    multiDexEnabled = true
  }
}

dependencies {
  // landscapist
  implementation(project(":landscapist"))
  implementation(project(":landscapist-animation"))
  implementation(project(":landscapist-placeholder"))
  implementation(project(":landscapist-transformation"))
  implementation(project(":landscapist-palette"))

  implementation(project(":glide"))
  implementation(project(":coil3"))
  implementation(project(":fresco"))
  implementation(project(":fresco-websupport"))

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
}
