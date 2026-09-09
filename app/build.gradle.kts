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
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    // The measurement classes are left out of a connected run that names nothing. Every one of
    // them claims the process before it measures anything (DeviceMeasure.claimTheProcess): a
    // second measurement in a process that has already measured one runs against a warmed loader
    // rather than against the other library, so it refuses to run rather than report a number
    // that flatters whichever went second. Running the whole suite in one invocation therefore
    // failed every measurement after the first, and `./gradlew :app:connectedDebugAndroidTest`
    // could not be green.
    //
    // Naming a class overrides this, which is how a measurement is meant to be run anyway, one
    // per invocation so it gets its own process:
    //
    //   ./gradlew :app:connectedDebugAndroidTest \
    //     -Pandroid.testInstrumentationRunnerArguments.class=com.github.skydoves.landscapistdemo.measure.ScrollComparisonTest#scrollCoil
    //
    // A package argument does not override it, so name the class rather than the package.
    testInstrumentationRunnerArguments["notPackage"] = "com.github.skydoves.landscapistdemo.measure"
  }
}

// Configure stability analyzer
composeStabilityAnalyzer {
  enabled.set(true)
}

dependencies {
  // landscapist
  implementation(project(":landscapist"))
  implementation(project(":landscapist-animation"))
  implementation(project(":landscapist-placeholder"))
  implementation(project(":landscapist-transformation"))
  implementation(project(":landscapist-palette"))
  implementation(project(":landscapist-zoomable"))
  implementation(project(":landscapist-image"))
  implementation(project(":landscapist-image-gallery"))

  implementation(project(":glide"))
  implementation(project(":coil3"))
  // Coil's own Compose API, so the playground can put AsyncImage next to LandscapistImage.
  implementation(libs.coil3.compose)
  implementation(project(":fresco"))
  implementation(project(":fresco-websupport"))

  // material
  implementation(libs.androidx.material)

  // androidx jetpack
  implementation(libs.androidx.core.ktx)

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

  // Test dependencies
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Direct Coil3 for performance comparison tests
  androidTestImplementation(libs.coil3.compose)
}
