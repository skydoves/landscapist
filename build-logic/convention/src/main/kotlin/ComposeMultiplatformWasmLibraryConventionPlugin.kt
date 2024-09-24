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

import com.android.build.gradle.LibraryExtension
import com.skydoves.landscapist.configureComposeMultiplatformWasm
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class ComposeMultiplatformWasmLibraryConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    with(target) {
      pluginManager.apply("com.android.library")
      pluginManager.apply("org.jetbrains.kotlin.multiplatform")
      pluginManager.apply("org.jetbrains.compose")
      pluginManager.apply("com.vanniktech.maven.publish")
      pluginManager.apply("binary-compatibility-validator")
      pluginManager.apply("androidx.baselineprofile")

      extensions.configure<LibraryExtension> libraryExtension@{
        extensions.configure<KotlinMultiplatformExtension> kmpExtension@{
          configureComposeMultiplatformWasm(this@libraryExtension, this@kmpExtension)
        }
      }

      val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
      tasks.withType(JavaCompile::class.java).configureEach {
        this.targetCompatibility = libs.findVersion("jvmTarget").get().toString()
        this.sourceCompatibility = libs.findVersion("jvmTarget").get().toString()
      }

      dependencies {
        add("baselineProfile", project(":benchmark-landscapist"))
      }
    }
  }
}
