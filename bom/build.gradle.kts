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

plugins {
  kotlin("jvm")
  id(libs.plugins.nexus.plugin.get().pluginId)
}

apply(from = "${rootDir}/scripts/publish-module.gradle.kts")

mavenPublishing {
  val artifactId = "landscapist-bom"
  coordinates(
    Configuration.artifactGroup,
    artifactId,
    rootProject.extra.get("libVersion").toString()
  )

  pom {
    name.set(artifactId)
  }
}

dependencies {
  constraints {
    api(project(":landscapist"))
    api(project(":landscapist-animation"))
    api(project(":landscapist-palette"))
    api(project(":landscapist-placeholder"))
    api(project(":landscapist-transformation"))
    api(project(":landscapist-zoomable"))
    api(project(":glide"))
    api(project(":coil"))
    api(project(":coil3"))
    api(project(":fresco"))
    api(project(":fresco-websupport"))
  }
}