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
  id(libs.plugins.baseline.profile.get().pluginId)
  id("landscapist.application.compose")
  id("landscapist.spotless")
}

android {
  namespace = "com.skydoves.benchmark.landscapist.app"
  compileSdk = Configuration.compileSdk
  defaultConfig {
    applicationId = "com.skydoves.benchmark.landscapist.app"
    minSdk = 23
    targetSdk = Configuration.targetSdk
    versionCode = Configuration.versionCode
    versionName = Configuration.versionName
  }

  buildTypes {
    create("benchmark") {
      isDebuggable = true
      signingConfig = getByName("debug").signingConfig
      matchingFallbacks += listOf("release")
    }
  }
}

dependencies {
  implementation(project(":landscapist"))
  implementation(project(":landscapist-animation"))
  implementation(project(":landscapist-palette"))
  implementation(project(":landscapist-placeholder"))
  implementation(project(":landscapist-transformation"))

  implementation(project(":glide"))
  implementation(project(":coil"))
  implementation(project(":fresco"))

  implementation(libs.androidx.material)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling)
  implementation(libs.androidx.compose.runtime)
  implementation(libs.androidx.compose.material)

  baselineProfile(project(":benchmark-landscapist"))
}