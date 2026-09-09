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
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.jetbrains.compose)
  alias(libs.plugins.compose.compiler)
  id("landscapist.spotless")
}

// The web playground, published to https://skydoves.github.io/landscapist/demo/ by
// .github/workflows/publish-docs.yml. Not published to Maven, so none of the library convention
// plugins apply here: they all bring com.android.library, maven publishing, explicitApi() and the
// API validator, and an app wants none of those.
kotlin {
  wasmJs {
    browser {
      // rootProject.name is LandscapistDemo, so without this the bundle would be called
      // LandscapistDemo-demo-web.js.
      commonWebpackConfig {
        outputFileName = "demo.js"
      }
    }
    binaries.executable()
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation(project(":landscapist"))
        implementation(project(":landscapist-core"))
        implementation(project(":landscapist-image"))
        implementation(project(":landscapist-animation"))
        implementation(project(":landscapist-placeholder"))
        implementation(project(":landscapist-zoomable"))

        implementation(libs.kotlinx.coroutines.core)

        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material)
        implementation(compose.ui)

        // Real Coil, not the landscapist wrapper: the comparison column is meant to be the other
        // library rather than this library's binding to it.
        implementation(libs.coil3)
        implementation(libs.coil3.compose)
        implementation(libs.coil3.network.ktor3)
      }
    }

    wasmJsMain {
      dependencies {
        // Coil's Ktor fetcher picks its engine off the classpath. landscapist-core has its own
        // copy but declares it as implementation, so it does not reach here.
        implementation(libs.ktor.engine.js)
      }
    }
  }
}
