/*
 * Designed and developed by 2020 skydoves (Jaewoong Eum)
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
import com.github.skydoves.landscapist.Dependencies
import com.github.skydoves.landscapist.Versions

plugins {
  id("com.android.application")
  id("kotlin-android")
  id("kotlin-kapt")
  id("dagger.hilt.android.plugin")
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

  lint {
    abortOnError = false
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  composeOptions {
    kotlinCompilerExtensionVersion = Versions.COMPOSE_COMPILER
  }

  buildFeatures {
    compose = true
  }

  packagingOptions {
    resources {
      excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
  }
}

dependencies {
  // material
  implementation(Dependencies.material)

  // androidx jetpack
  implementation(Dependencies.coreKtx)

  // hilt
  implementation(Dependencies.hiltAndroid)
  kapt(Dependencies.hiltCompiler)

  // compose
  implementation(Dependencies.composeUI)
  implementation(Dependencies.composeActivity)
  implementation(Dependencies.composeMaterial)
  implementation(Dependencies.composeMaterialIcon)
  implementation(Dependencies.composeFoundation)
  implementation(Dependencies.composeFoundationLayout)
  implementation(Dependencies.composeAnimation)
  implementation(Dependencies.composeRuntime)
  implementation(Dependencies.composeTooling)
  implementation(Dependencies.composeConstraintLayout)

  implementation("androidx.multidex:multidex:2.0.1")

  // landscapist
  implementation(project(":fresco"))
  implementation(project(":fresco-websupport"))
  implementation(project(":glide"))
  implementation(project(":coil"))
}

